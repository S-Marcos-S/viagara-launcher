#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""
Script to automatically curate and synchronize high-quality wallpapers from open sources
into the Viagara Launcher repository.

Generates:
- Optimized full-resolution WebP images in wallpapers/full/
- Lightweight WebP thumbnails in wallpapers/thumbnails/
- Updates wallpapers/catalog.json and app/src/main/assets/wallpapers/catalog.json
"""

import argparse
import io
import json
import os
import sys
import time
import urllib.request
import urllib.error
from typing import Dict, List, Optional, Any

try:
    from PIL import Image
    Image.MAX_IMAGE_PIXELS = None
except ImportError:
    print("Pillow (PIL) is required. Install with: pip install Pillow")
    sys.exit(1)

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CATALOG_PATH = os.path.join(BASE_DIR, "wallpapers", "catalog.json")
ASSETS_CATALOG_PATH = os.path.join(BASE_DIR, "app", "src", "main", "assets", "wallpapers", "catalog.json")
FULL_DIR = os.path.join(BASE_DIR, "wallpapers", "full")
THUMB_DIR = os.path.join(BASE_DIR, "wallpapers", "thumbnails")

GITHUB_RAW_BASE = "https://raw.githubusercontent.com/S-Marcos-S/viagara-launcher/main/wallpapers"

USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

ALL_CATEGORIES_LIST = [
    "oled", "anime", "games", "minimal", "abstract", "nature",
    "space", "cyberpunk", "city", "cars", "animals", "fantasy"
]

CATEGORY_QUERIES = {
    "oled": "https://wallhaven.cc/api/v1/search?categories=100&colors=000000&ratios=9x16,10x16&sorting=favorites&purity=100",
    "anime": "https://wallhaven.cc/api/v1/search?categories=010&ratios=9x16,10x16&sorting=favorites&purity=100",
    "games": "https://wallhaven.cc/api/v1/search?q=video+games&ratios=9x16,10x16&sorting=favorites&purity=100",
    "minimal": "https://wallhaven.cc/api/v1/search?categories=100&q=minimalism&ratios=9x16,10x16&sorting=favorites&purity=100",
    "abstract": "https://wallhaven.cc/api/v1/search?categories=100&q=abstract&ratios=9x16,10x16&sorting=favorites&purity=100",
    "nature": "https://wallhaven.cc/api/v1/search?categories=100&q=nature&ratios=9x16,10x16&sorting=favorites&purity=100",
    "space": "https://wallhaven.cc/api/v1/search?categories=100&q=space&ratios=9x16,10x16&sorting=favorites&purity=100",
    "cyberpunk": "https://wallhaven.cc/api/v1/search?q=cyberpunk&ratios=9x16,10x16&sorting=favorites&purity=100",
    "city": "https://wallhaven.cc/api/v1/search?categories=100&q=city&ratios=9x16,10x16&sorting=favorites&purity=100",
    "cars": "https://wallhaven.cc/api/v1/search?categories=100&q=car&ratios=9x16,10x16&sorting=favorites&purity=100",
    "animals": "https://wallhaven.cc/api/v1/search?categories=100&q=animals&ratios=9x16,10x16&sorting=favorites&purity=100",
    "fantasy": "https://wallhaven.cc/api/v1/search?q=fantasy&ratios=9x16,10x16&sorting=favorites&purity=100",
}

# Categories that must NOT contain anime/character wallpapers
STRICT_NON_CHARACTER_CATEGORIES = {
    "oled", "space", "nature", "minimal", "abstract", "city", "cars", "animals"
}

ANIME_CHARACTER_TAGS = {
    "anime", "anime girls", "anime boy", "manga", "pixiv", "girl", "girls", "women",
    "men", "boy", "female", "male", "maid", "maid outfit", "pantyhose", "heels", "high heels",
    "bikini", "cosplay", "cgi", "character", "character design", "genshin", "genshin impact",
    "honkai", "honkai star rail", "zenless zone zero", "blue archive", "game art", "comic", "comics",
    "illustration", "fantasy girl", "fantasy art", "waifu", "vtuber", "digital art", "drawing",
    "model", "actress", "celebrity", "portrait display", "portrait", "crossfire", "ghostblade",
}


def fetch_json(url: str, timeout: int = 15, retries: int = 2) -> Optional[Dict[str, Any]]:
    for attempt in range(retries + 1):
        req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
        try:
            with urllib.request.urlopen(req, timeout=timeout) as response:
                return json.loads(response.read().decode("utf-8"))
        except urllib.error.HTTPError as e:
            if e.code == 429:
                wait_time = 3.0 * (attempt + 1)
                print(f"Rate limited (429) fetching {url}, waiting {wait_time}s...")
                time.sleep(wait_time)
                continue
            print(f"HTTP error fetching {url}: {e}")
            return None
        except Exception as e:
            print(f"Error fetching {url}: {e}")
            return None
    return None


def fetch_bytes(url: str, timeout: int = 25) -> Optional[bytes]:
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    try:
        with urllib.request.urlopen(req, timeout=timeout) as response:
            return response.read()
    except Exception as e:
        print(f"Error downloading {url}: {e}")
        return None


def get_dominant_color_hex(img: Image.Image) -> str:
    small = img.copy().resize((1, 1), Image.Resampling.BOX)
    r, g, b = small.getpixel((0, 0))[:3]
    return f"#{r:02X}{g:02X}{b:02X}"


def clean_name(raw_name: str) -> str:
    cleaned = raw_name.replace("-", " ").replace("_", " ").strip()
    return " ".join(w.capitalize() for w in cleaned.split() if w)


def process_and_save_wallpaper(
    item_id: str,
    image_bytes: bytes,
    full_path: str,
    thumb_path: str,
) -> bool:
    try:
        img = Image.open(io.BytesIO(image_bytes)).convert("RGB")
        w, h = img.size

        # Resize full image if excessively large to keep storage efficient
        max_h = 2560
        if h > max_h:
            new_w = int(w * (max_h / h))
            full_img = img.resize((new_w, max_h), Image.Resampling.LANCZOS)
        else:
            full_img = img

        full_img.save(full_path, format="WEBP", quality=85, method=4)

        # Generate thumbnail (target height 720)
        thumb_h = 720
        thumb_w = int(w * (thumb_h / h))
        thumb_img = img.resize((thumb_w, thumb_h), Image.Resampling.LANCZOS)
        thumb_img.save(thumb_path, format="WEBP", quality=75, method=4)

        return True
    except Exception as e:
        print(f"Failed to process image for {item_id}: {e}")
        return False


def main():
    parser = argparse.ArgumentParser(description="Sync curated wallpapers into repository.")
    parser.add_argument("--max-per-category", type=int, default=2, help="Max new items per category per run")
    parser.add_argument("--max-total", type=int, default=12, help="Max total new items added per run")
    parser.add_argument("--categories", type=str, default=",".join(ALL_CATEGORIES_LIST), help="Comma-separated categories")
    parser.add_argument("--dry-run", action="store_true", help="Inspect without modifying files")
    args = parser.parse_args()

    os.makedirs(FULL_DIR, exist_ok=True)
    os.makedirs(THUMB_DIR, exist_ok=True)
    os.makedirs(os.path.dirname(ASSETS_CATALOG_PATH), exist_ok=True)

    default_categories_data = [
        {"id": "all", "label": "Todos"},
        {"id": "oled", "label": "OLED"},
        {"id": "anime", "label": "Anime"},
        {"id": "games", "label": "Jogos"},
        {"id": "minimal", "label": "Minimalista"},
        {"id": "abstract", "label": "Abstrato"},
        {"id": "nature", "label": "Natureza"},
        {"id": "space", "label": "Espaço"},
        {"id": "cyberpunk", "label": "Cyberpunk"},
        {"id": "city", "label": "Cidades"},
        {"id": "cars", "label": "Carros"},
        {"id": "animals", "label": "Animais"},
        {"id": "fantasy", "label": "Fantasia"},
    ]

    if not os.path.exists(CATALOG_PATH):
        catalog = {
            "version": 1,
            "categories": default_categories_data,
            "wallpapers": [],
        }
    else:
        with open(CATALOG_PATH, "r", encoding="utf-8") as f:
            catalog = json.load(f)

    catalog["categories"] = default_categories_data

    existing_wallpapers = catalog.get("wallpapers", [])
    existing_ids = {w["id"] for w in existing_wallpapers}
    existing_urls = {w["full_url"] for w in existing_wallpapers}

    target_categories = [c.strip() for c in args.categories.split(",") if c.strip() in CATEGORY_QUERIES]
    added_count = 0
    new_items: List[Dict[str, Any]] = []

    # If the user raised max_total significantly higher than max_per_category * num_cats,
    # scale max_per_category so the total requested can actually be reached across categories.
    effective_max_per_cat = args.max_per_category
    if target_categories:
        scaled = (args.max_total + len(target_categories) - 1) // len(target_categories)
        if args.max_per_category <= 0 or args.max_total > (args.max_per_category * len(target_categories)):
            effective_max_per_cat = max(args.max_per_category, scaled)
        if effective_max_per_cat <= 0:
            effective_max_per_cat = 1

    print(f"Starting wallpaper sync (max total: {args.max_total}, max per category: {effective_max_per_cat}, categories: {', '.join(target_categories)})...")

    for cat in target_categories:
        if added_count >= args.max_total:
            break

        base_query_url = CATEGORY_QUERIES[cat]
        cat_added = 0
        page = 1
        max_pages = 8  # up to 8 pages per category (192 results evaluated)

        print(f"\nQuerying category '{cat}'...")

        while cat_added < effective_max_per_cat and added_count < args.max_total and page <= max_pages:
            paginated_url = f"{base_query_url}&page={page}"
            res = fetch_json(paginated_url)
            if not res or "data" not in res or not res["data"]:
                break

            items = res["data"]
            for item in items:
                if added_count >= args.max_total or cat_added >= effective_max_per_cat:
                    break

                wh_id = item["id"]
                wallpaper_id = f"wallpaper_wh_{wh_id}"
                if wallpaper_id in existing_ids:
                    continue

                dim_x = item.get("dimension_x", 0)
                dim_y = item.get("dimension_y", 0)
                if dim_y <= dim_x or dim_y < 1600:
                    continue

                image_url = item.get("path")
                if not image_url or image_url in existing_urls:
                    continue

                # Fetch extra details for title and tags
                time.sleep(1.2)
                detail = fetch_json(f"https://wallhaven.cc/api/v1/w/{wh_id}")
                tags = [cat]
                author = "Wallhaven"
                title = None

                disallowed_title_tags = {
                    "vertical", "portrait display", "simple background", "no people",
                    "women", "girl", "girls", "men", "boy", "model", "brunette", "blonde",
                    "long hair", "short hair", "looking at viewer", "asian", "white hair",
                    "black hair", "anime", "anime girls", "drawn", "digital art", "artwork",
                    "wallpaper", "photoshop", "picture", "minimalism", "abstract", "nature", "space", "oled"
                }

                if detail and "data" in detail:
                    d = detail["data"]
                    uploader = d.get("uploader", {}).get("username")
                    if uploader:
                        author = uploader
                    d_tags = [t["name"] for t in d.get("tags", []) if t.get("name")]
                    wh_cat = d.get("category", "")

                    if any(t.lower() in ["error", "errors", "glitch", "broken", "404"] for t in d_tags):
                        print(f"Skipping candidate {wh_id} due to error/glitch tags.")
                        continue

                    # Strict category check for non-character categories
                    if cat in STRICT_NON_CHARACTER_CATEGORIES:
                        if wh_cat == "anime":
                            print(f"Skipping candidate {wh_id} for '{cat}' (Wallhaven category is anime).")
                            continue
                        if any(t.lower() in ANIME_CHARACTER_TAGS for t in d_tags):
                            print(f"Skipping candidate {wh_id} for '{cat}' due to character/anime tags.")
                            continue

                    filtered_tags = [t for t in d_tags if t.lower() not in disallowed_title_tags]
                    if filtered_tags:
                        title = clean_name(filtered_tags[0])
                        tags.extend(filtered_tags[:3])

                if not title:
                    title = f"{cat.capitalize()} #{wh_id.upper()}"

                colors = item.get("colors", [])
                primary_color = colors[0] if colors else "#000000"

                full_filename = f"{wallpaper_id}.webp"
                thumb_filename = f"{wallpaper_id}.webp"
                full_dest = os.path.join(FULL_DIR, full_filename)
                thumb_dest = os.path.join(THUMB_DIR, thumb_filename)

                if args.dry_run:
                    print(f"[DRY-RUN] Would download {image_url} -> {full_dest} ({title} by {author})")
                    cat_added += 1
                    added_count += 1
                    continue

                print(f"Downloading {image_url} ({title} for '{cat}')...")
                img_bytes = fetch_bytes(image_url)
                if not img_bytes:
                    print(f"Failed to download image for {wh_id}, skipping.")
                    continue

                print(f"Processing and converting to WebP...")
                success = process_and_save_wallpaper(wallpaper_id, img_bytes, full_dest, thumb_dest)
                if not success:
                    continue

                new_entry = {
                    "id": wallpaper_id,
                    "name": title,
                    "author": author,
                    "thumbnail_url": f"{GITHUB_RAW_BASE}/thumbnails/{thumb_filename}",
                    "full_url": f"{GITHUB_RAW_BASE}/full/{full_filename}",
                    "primary_color": primary_color,
                    "category": cat,
                    "tags": list(dict.fromkeys(tags)),
                }

                existing_ids.add(wallpaper_id)
                existing_urls.add(image_url)
                new_items.append(new_entry)
                cat_added += 1
                added_count += 1
                print(f"Successfully added '{title}' (id: {wallpaper_id}) to category '{cat}'")

            page += 1

    if args.dry_run:
        print(f"\n[DRY-RUN] Finished. Would have added {added_count} wallpapers.")
        return

    if not new_items:
        print("\nNo new wallpapers were added.")
        return

    # Append new items to existing catalog
    catalog["wallpapers"].extend(new_items)

    # Save to wallpapers/catalog.json
    with open(CATALOG_PATH, "w", encoding="utf-8") as f:
        json.dump(catalog, f, indent=2, ensure_ascii=False)
        f.write("\n")

    # Sync to assets catalog
    with open(ASSETS_CATALOG_PATH, "w", encoding="utf-8") as f:
        json.dump(catalog, f, indent=2, ensure_ascii=False)
        f.write("\n")

    # Update CATALOG.md visual markdown
    try:
        from manage_wallpapers import update_catalog_md
        update_catalog_md(catalog)
    except Exception as e:
        print(f"Notice: Could not update CATALOG.md: {e}")

    print(f"\nSuccessfully added {len(new_items)} new wallpapers and updated catalog files.")
    for item in new_items:
        print(f"- [{item['category']}] {item['name']} by {item['author']} ({item['id']})")


if __name__ == "__main__":
    main()
