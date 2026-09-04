import types
if not hasattr(types, "NoneType"):
    types.NoneType = type(None)

import asyncio
import json
import logging
from typing import Optional, Dict, Any
from pikpakapi import PikPakApi

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("PikPakPythonResolver")

async def _resolve_stream_async(
    username: str,
    password: str,
    magnet: str,
    file_id: Optional[str] = None,
    file_name: Optional[str] = None
) -> Dict[str, Any]:
    try:
        logger.info(f"PikPakApi login for {username}...")
        client = PikPakApi(username=username, password=password)
        await client.login()
        logger.info(f"Login OK, user_id={client.user_id}")

        resolved_file_id = file_id
        target_file_info = None

        def is_sample_file(name: str) -> bool:
            n = (name or "").lower()
            return "sample" in n or n.startswith("sample")

        # Step 1: If file_id is provided, verify it is not a sample file
        existing_movie_found = False
        if file_id:
            try:
                info = await client.get_download_url(file_id)
                fname = info.get("name", "")
                fsize = int(info.get("size") or info.get("file_size") or 0)
                parent_id = info.get("parent_id")
                
                if is_sample_file(fname) or (0 < fsize < 80 * 1024 * 1024):
                    logger.warning(f"Provided file_id={file_id} is a sample ('{fname}', {fsize} bytes). Checking parent folder {parent_id}...")
                    target_file_info = None
                    resolved_file_id = None
                    if parent_id:
                        try:
                            flist = await client.file_list(parent_id=parent_id)
                            files = flist.get("files", [])
                            real_files = [f for f in files if f.get("kind") != "drive#folder" and not is_sample_file(f.get("name", "")) and int(f.get("size", 0) or f.get("file_size", 0) or 0) > 80 * 1024 * 1024]
                            if real_files:
                                best = max(real_files, key=lambda f: int(f.get("size", 0) or f.get("file_size", 0) or 0))
                                resolved_file_id = best.get("id")
                                target_file_info = await client.get_download_url(resolved_file_id)
                                existing_movie_found = True
                                logger.info(f"🎉 Recovered real full movie from parent folder: '{best.get('name')}' (id={resolved_file_id})")
                        except Exception as pe:
                            logger.warning(f"Failed inspecting parent folder {parent_id}: {pe}")
                elif fsize >= 80 * 1024 * 1024:
                    existing_movie_found = True
                    target_file_info = info
                    resolved_file_id = file_id
                    logger.info(f"Existing file_id={file_id} is verified full movie '{fname}' ({fsize} bytes)")
                else:
                    target_file_info = None
                    resolved_file_id = None
            except Exception as e:
                logger.warning(f"get_download_url for file_id={file_id} failed: {e}")
                target_file_info = None
                resolved_file_id = None

        # Step 2: Queue offline download for magnet if we do not already have the verified movie
        if magnet:
            try:
                logger.info(f"Calling client.offline_download for magnet {magnet[:40]}...")
                task_resp = await client.offline_download(file_url=magnet, name=file_name)
                logger.info(f"offline_download response: {task_resp}")

                if not existing_movie_found:
                    task_obj = task_resp.get("task", {})
                    file_obj = task_resp.get("file", {})
                    task_id = task_obj.get("id")
                    new_file_id = file_obj.get("id") or task_obj.get("file_id")

                    if task_id and (task_obj.get("phase") in ["PHASE_TYPE_RUNNING", "PHASE_TYPE_PENDING"] or not new_file_id):
                        for _ in range(8):
                            await asyncio.sleep(1)
                            try:
                                tasks_list = await client.offline_list()
                                t_list = tasks_list.get("tasks", [])
                                cur = next((t for t in t_list if t.get("id") == task_id), None)
                                if cur:
                                    if cur.get("file_id"):
                                        new_file_id = cur.get("file_id")
                                    if cur.get("phase") == "PHASE_TYPE_COMPLETE":
                                        break
                            except Exception:
                                pass

                    if new_file_id:
                        try:
                            flist = await client.file_list(parent_id=new_file_id)
                            files = flist.get("files", [])
                            if files:
                                real_files = [f for f in files if f.get("kind") != "drive#folder" and not is_sample_file(f.get("name", ""))]
                                non_small = [f for f in real_files if int(f.get("size", 0) or f.get("file_size", 0) or 0) > 80 * 1024 * 1024]
                                candidates = non_small if non_small else real_files
                                if candidates:
                                    best = max(candidates, key=lambda f: int(f.get("size", 0) or f.get("file_size", 0) or 0))
                                    resolved_file_id = best.get("id", new_file_id)
                                    target_file_info = await client.get_download_url(resolved_file_id)
                                    existing_movie_found = True
                        except Exception as e:
                            logger.warning(f"Error inspecting files in folder: {e}")
            except Exception as e:
                logger.warning(f"offline_download failed or rejected: {e}")

        if not target_file_info and resolved_file_id:
            try:
                candidate_info = await client.get_download_url(resolved_file_id)
                cand_name = candidate_info.get("name", "")
                if not is_sample_file(cand_name):
                    target_file_info = candidate_info
            except Exception:
                pass

        if not target_file_info:
            return {
                "success": False,
                "error": "No valid movie file info could be retrieved from PikPak API"
            }

        fname = target_file_info.get("name", "")
        fsize = int(target_file_info.get("size") or target_file_info.get("file_size") or 0)
        if is_sample_file(fname):
            return {
                "success": False,
                "error": f"Refusing to stream sample preview file '{fname}' ({fsize} bytes)"
            }

        # Step 3: Extract best stream URL
        stream_url = ""
        medias = target_file_info.get("medias", [])
        for m in medias:
            link = m.get("link", {})
            u = link.get("url")
            if u:
                stream_url = u
                break

        if not stream_url:
            stream_url = target_file_info.get("web_content_link") or ""

        if not stream_url:
            return {
                "success": False,
                "error": "PikPak returned file info without downloadable stream URL",
                "file_id": resolved_file_id
            }

        return {
            "success": True,
            "stream_url": stream_url,
            "file_id": resolved_file_id,
            "name": fname
        }

    except Exception as e:
        logger.error(f"PikPak resolver fatal error: {e}", exc_info=True)
        return {
            "success": False,
            "error": str(e),
            "error_type": type(e).__name__
        }

def resolve_stream(
    username: str,
    password: str,
    magnet: str,
    file_id: Optional[str] = None,
    file_name: Optional[str] = None
) -> str:
    """
    Synchronous entry point called by Kotlin via Chaquopy.
    Returns JSON string with result.
    """
    try:
        loop = asyncio.new_event_loop()
        asyncio.set_event_loop(loop)
        result = loop.run_until_complete(
            _resolve_stream_async(username, password, magnet, file_id, file_name)
        )
        loop.close()
        return json.dumps(result)
    except Exception as e:
        return json.dumps({
            "success": False,
            "error": str(e),
            "error_type": type(e).__name__
        })
