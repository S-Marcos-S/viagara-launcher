#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""
Script to list, visualize, and remove curated wallpapers from the repository.
Also generates wallpapers/CATALOG.md for visual browsing on GitHub.
Supports removing multiple wallpapers at once via IDs, keywords, or category.
"""

import argparse
import json
import os
import re
import sys
from typing import List, Set, Dict, Any, Tuple

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CATALOG_PATH = os.path.join(BASE_DIR, "wallpapers", "catalog.json")
ASSETS_CATALOG_PATH = os.path.join(BASE_DIR, "app", "src", "main", "assets", "wallpapers", "catalog.json")
FULL_DIR = os.path.join(BASE_DIR, "wallpapers", "full")
THUMB_DIR = os.path.join(BASE_DIR, "wallpapers", "thumbnails")
CATALOG_MD_PATH = os.path.join(BASE_DIR, "wallpapers", "CATALOG.md")


def load_catalog() -> Dict[str, Any]:
    if not os.path.exists(CATALOG_PATH):
        print(f"Error: {CATALOG_PATH} not found.")
        sys.exit(1)
    with open(CATALOG_PATH, "r", encoding="utf-8") as f:
        return json.load(f)


def save_catalog(catalog: Dict[str, Any]):
    with open(CATALOG_PATH, "w", encoding="utf-8") as f:
        json.dump(catalog, f, indent=2, ensure_ascii=False)
        f.write("\n")
    with open(ASSETS_CATALOG_PATH, "w", encoding="utf-8") as f:
        json.dump(catalog, f, indent=2, ensure_ascii=False)
        f.write("\n")


def generate_catalog_markdown(catalog: Dict[str, Any], for_github_summary: bool = False) -> str:
    wallpapers = catalog.get("wallpapers", [])
    categories = catalog.get("categories", [])
    cat_labels = {c["id"]: c.get("label", c["id"]) for c in categories}

    by_cat: Dict[str, List[Dict[str, Any]]] = {}
    for w in wallpapers:
        by_cat.setdefault(w.get("category", "outros"), []).append(w)

    lines = []
    lines.append("# 🖼️ Catálogo Visual de Papéis de Parede\n")
    lines.append(f"Total de papéis de parede: **{len(wallpapers)}**\n")

    for cat_id, items in by_cat.items():
        label = cat_labels.get(cat_id, cat_id.capitalize())
        lines.append(f"### {label} ({len(items)})\n")
        lines.append("| Prévia | ID | Nome | Tags |")
        lines.append("| :---: | :--- | :--- | :--- |")
        for item in items:
            wid = item["id"]
            name = item.get("name", "Sem título")
            if for_github_summary:
                img_url = item.get("thumbnail_url", f"thumbnails/{wid}.webp")
            else:
                img_url = f"thumbnails/{wid}.webp"
            tags_str = ", ".join(item.get("tags", []))
            lines.append(f'| <img src="{img_url}" width="80" alt="{name}" /> | `{wid}` | **{name}** | {tags_str} |')
        lines.append("")

    return "\n".join(lines)


def update_catalog_md(catalog: Dict[str, Any]):
    md = generate_catalog_markdown(catalog, for_github_summary=False)
    with open(CATALOG_MD_PATH, "w", encoding="utf-8") as f:
        f.write(md)
    print(f"Updated {CATALOG_MD_PATH}")


def write_to_step_summary(content: str):
    summary_path = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary_path:
        with open(summary_path, "a", encoding="utf-8") as f:
            f.write(content + "\n")


def parse_ids_input(raw_input: str) -> Tuple[List[str], Set[str]]:
    """
    Parses messy raw input containing multiple IDs.
    Supports delimiters: commas, semicolons, newlines, pipes, spaces, tabs.
    Supports markdown backticks, bullet dashes, file extensions (.webp/.jpg),
    and URL paths. Returns (cleaned_original_list, all_matching_variants_set).
    """
    if not raw_input:
        return [], set()

    tokens = re.split(r'[,;\n\r\t| ]+', raw_input)
    original_cleaned: List[str] = []
    variants: Set[str] = set()

    for token in tokens:
        t = token.strip()
        # Remove surrounding markdown symbols: `, ", ', *, -, #, [, ], (, )
        t = re.sub(r"^[`'\"*#\-\[\]()]+", "", t)
        t = re.sub(r"[`'\"*#\-\[\]()]+$", "", t)
        t = t.strip()
        if not t:
            continue

        # If it's a URL or path, get basename
        if "/" in t or "\\" in t:
            t = os.path.basename(t)

        # Strip file extensions
        for ext in [".webp", ".jpg", ".png", ".jpeg"]:
            if t.lower().endswith(ext):
                t = t[:-len(ext)]
        t = t.strip()

        if not t:
            continue

        if t not in original_cleaned:
            original_cleaned.append(t)

        variants.add(t)
        variants.add(t.lower())

        # Generate prefixes/suffixes for Wallhaven or generic IDs
        if t.startswith("wallpaper_wh_"):
            suffix = t[len("wallpaper_wh_"):]
            variants.add(suffix)
            variants.add(f"wh_{suffix}")
        elif t.startswith("wallpaper_"):
            suffix = t[len("wallpaper_"):]
            variants.add(suffix)
        elif t.startswith("wh_"):
            suffix = t[len("wh_"):]
            variants.add(suffix)
            variants.add(f"wallpaper_wh_{suffix}")
        else:
            variants.add(f"wallpaper_wh_{t}")
            variants.add(f"wallpaper_{t}")
            variants.add(f"wh_{t}")

    return original_cleaned, variants


def parse_keywords_input(raw_keywords: str) -> List[str]:
    """Splits multiple keywords by comma, semicolon, newline, pipe."""
    if not raw_keywords:
        return []
    tokens = re.split(r'[,;\n\r|]+', raw_keywords)
    return [t.strip().lower() for t in tokens if t.strip()]


def remove_wallpapers(
    catalog: Dict[str, Any],
    ids_to_remove: Set[str],
    raw_ids_list: List[str] = None,
    keywords_raw: str = "",
    category: str = "",
    dry_run: bool = False,
) -> int:
    wallpapers = catalog.get("wallpapers", [])
    kept = []
    removed = []

    keywords = parse_keywords_input(keywords_raw)
    matched_requested_ids: Set[str] = set()

    for w in wallpapers:
        wid = w["id"]
        wid_clean = wid.replace("wallpaper_wh_", "").replace("wallpaper_", "")

        # Match ID
        match_id = (
            wid in ids_to_remove
            or wid.lower() in ids_to_remove
            or wid_clean in ids_to_remove
            or wid_clean.lower() in ids_to_remove
            or f"wh_{wid_clean}" in ids_to_remove
        )

        if match_id:
            matched_requested_ids.add(wid)
            matched_requested_ids.add(wid_clean)
            matched_requested_ids.add(f"wh_{wid_clean}")

        # Match Keywords (across title and tags)
        match_keyword = False
        if keywords:
            name_lower = w.get("name", "").lower()
            tags_lower = [t.lower() for t in w.get("tags", [])]
            for kw in keywords:
                if kw in name_lower or any(kw in t for t in tags_lower):
                    match_keyword = True
                    break

        # Match Category
        match_category = False
        if category and category != "none":
            match_category = (w.get("category") == category)

        if match_id or match_keyword or match_category:
            removed.append(w)
        else:
            kept.append(w)

    unmatched_ids = []
    if raw_ids_list:
        for orig in raw_ids_list:
            orig_clean = orig.replace("wallpaper_wh_", "").replace("wallpaper_", "")
            if orig not in matched_requested_ids and orig_clean not in matched_requested_ids:
                unmatched_ids.append(orig)

    if not removed:
        print("Nenhum papel de parede encontrado com os critérios fornecidos.")
        msg = "### ℹ️ Nenhum papel de parede correspondeu aos filtros para remoção."
        if unmatched_ids:
            msg += f"\n\n> ⚠️ **IDs não encontrados no catálogo:** `{', '.join(unmatched_ids)}`"
        write_to_step_summary(msg)
        return 0

    print(f"\nPapéis de parede a remover ({len(removed)}):")
    summary_lines = [
        f"### 🗑️ Papéis de parede removidos ({len(removed)}):",
        f"Total restante no catálogo: **{len(kept)}**",
        "",
        "| Prévia | ID | Nome | Categoria |",
        "| :---: | :--- | :--- | :--- |",
    ]

    for r in removed:
        wid = r["id"]
        name = r.get("name", "")
        cat = r.get("category", "")
        thumb_url = r.get("thumbnail_url", "")
        print(f"- [{cat}] `{wid}` - {name}")
        summary_lines.append(f'| <img src="{thumb_url}" width="60" alt="{name}" /> | `{wid}` | {name} | {cat} |')

    if unmatched_ids:
        print(f"\n⚠️ Atenção: Os seguintes IDs não foram encontrados (já removidos ou incorretos): {', '.join(unmatched_ids)}")
        summary_lines.append(f"\n> ⚠️ **Aviso:** Os seguintes IDs não foram encontrados no catálogo: `{', '.join(unmatched_ids)}`")

    if dry_run:
        print("\n[DRY-RUN] Nenhuma alteração foi salva no catálogo ou no disco.")
        summary_lines.append("\n> **[DRY-RUN]** Simulação executada. Nenhum arquivo foi modificado.")
        write_to_step_summary("\n".join(summary_lines))
        return len(removed)

    # Delete image files from disk
    deleted_files = 0
    for r in removed:
        wid = r["id"]
        full_file = os.path.join(FULL_DIR, f"{wid}.webp")
        thumb_file = os.path.join(THUMB_DIR, f"{wid}.webp")
        if os.path.exists(full_file):
            os.remove(full_file)
            deleted_files += 1
        if os.path.exists(thumb_file):
            os.remove(thumb_file)
            deleted_files += 1

    catalog["wallpapers"] = kept
    save_catalog(catalog)
    update_catalog_md(catalog)

    print(f"\nSucesso: {len(removed)} papéis de parede removidos e {deleted_files} arquivos de imagem deletados.")
    write_to_step_summary("\n".join(summary_lines))
    return len(removed)


def main():
    parser = argparse.ArgumentParser(description="Manage and clean curated wallpapers.")
    parser.add_argument("--action", type=str, choices=["list", "remove", "update-md"], default=None)
    parser.add_argument("--ids", type=str, default=None, help="IDs to remove (supports multiple, comma/space/line separated)")
    parser.add_argument("--keyword", type=str, default=None, help="Keywords to search in title/tags for removal")
    parser.add_argument("--category", type=str, default=None, help="Category to purge (e.g. oled, anime)")
    parser.add_argument("--dry-run", action="store_true", default=None, help="Simulate without deleting")
    args = parser.parse_args()

    # Environment variable fallbacks for seamless GitHub Actions integration
    action = args.action or os.environ.get("ACTION", "list")
    if action == "list_all":
        action = "list"

    ids_input = args.ids if args.ids is not None else os.environ.get("WALLPAPER_IDS", "")
    keyword_input = args.keyword if args.keyword is not None else os.environ.get("KEYWORD", "")
    category_input = args.category if args.category is not None else os.environ.get("CATEGORY", "none")
    dry_run = args.dry_run if args.dry_run is not None and args.dry_run else (os.environ.get("DRY_RUN", "").lower() in ("true", "1", "yes"))

    catalog = load_catalog()

    if action == "update-md":
        update_catalog_md(catalog)
        return

    if action == "list":
        update_catalog_md(catalog)
        summary_md = generate_catalog_markdown(catalog, for_github_summary=True)
        write_to_step_summary(summary_md)
        print(f"Catálogo listado com sucesso ({len(catalog.get('wallpapers', []))} itens).")
        return

    if action == "remove":
        orig_ids, ids_set = parse_ids_input(ids_input)

        if not ids_set and not keyword_input and (not category_input or category_input == "none"):
            print("Erro: Para remover, informe ao menos um ID, palavra-chave ou categoria.")
            write_to_step_summary("### ⚠️ Erro: Nenhum critério de remoção foi informado (IDs, palavra-chave ou categoria).")
            sys.exit(1)

        print(f"Critérios de remoção:")
        if orig_ids:
            print(f"- Total de IDs informados: {len(orig_ids)} ({', '.join(orig_ids)})")
        if keyword_input:
            print(f"- Palavras-chave: {keyword_input}")
        if category_input and category_input != "none":
            print(f"- Categoria: {category_input}")
        if dry_run:
            print("- Modo DRY-RUN ativado (simulação)")

        remove_wallpapers(
            catalog=catalog,
            ids_to_remove=ids_set,
            raw_ids_list=orig_ids,
            keywords_raw=keyword_input,
            category=category_input,
            dry_run=dry_run,
        )


if __name__ == "__main__":
    main()
