#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""
Script to list, visualize, and remove curated wallpapers from the repository.
Also generates wallpapers/CATALOG.md for visual browsing on GitHub.
"""

import argparse
import json
import os
import sys
from typing import List, Set, Dict, Any

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


def remove_wallpapers(
    catalog: Dict[str, Any],
    ids_to_remove: Set[str],
    keyword: str = "",
    category: str = "",
    dry_run: bool = False,
) -> int:
    wallpapers = catalog.get("wallpapers", [])
    kept = []
    removed = []

    keyword_lower = keyword.strip().lower() if keyword else ""

    for w in wallpapers:
        wid = w["id"]
        # Match ID (accepts full id or short suffix, e.g. wallpaper_wh_123 or wh_123 or 123)
        match_id = (
            wid in ids_to_remove
            or wid.replace("wallpaper_wh_", "") in ids_to_remove
            or wid.replace("wallpaper_", "") in ids_to_remove
        )

        match_keyword = False
        if keyword_lower:
            match_name = keyword_lower in w.get("name", "").lower()
            match_tags = any(keyword_lower in t.lower() for t in w.get("tags", []))
            match_keyword = match_name or match_tags

        match_category = False
        if category and category != "none":
            match_category = (w.get("category") == category)

        if match_id or match_keyword or match_category:
            removed.append(w)
        else:
            kept.append(w)

    if not removed:
        print("Nenhum papel de parede encontrado com os critérios fornecidos.")
        write_to_step_summary("### ℹ️ Nenhum papel de parede correspondeu aos filtros para remoção.")
        return 0

    print(f"\nPapéis de parede a remover ({len(removed)}):")
    summary_lines = [f"### 🗑️ Papéis de parede removidos ({len(removed)}):", "| Prévia | ID | Nome | Categoria |", "| :---: | :--- | :--- | :--- |"]

    for r in removed:
        wid = r["id"]
        name = r.get("name", "")
        cat = r.get("category", "")
        thumb_url = r.get("thumbnail_url", "")
        print(f"- [{cat}] `{wid}` - {name}")
        summary_lines.append(f'| <img src="{thumb_url}" width="60" alt="{name}" /> | `{wid}` | {name} | {cat} |')

    if dry_run:
        print("\n[DRY-RUN] Nenhuma alteração foi salva no catálogo ou no disco.")
        summary_lines.append("\n> **[DRY-RUN]** Simulação executada. Nenhum arquivo foi removido.")
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

    print(f"\nSucesso: {len(removed)} papéis de parede removidos e {deleted_files} arquivos deletados.")
    write_to_step_summary("\n".join(summary_lines))
    return len(removed)


def main():
    parser = argparse.ArgumentParser(description="Manage and clean curated wallpapers.")
    parser.add_argument("--action", type=str, choices=["list", "remove", "update-md"], default="list")
    parser.add_argument("--ids", type=str, default="", help="Comma or space separated IDs to remove")
    parser.add_argument("--keyword", type=str, default="", help="Keyword to search in title/tags for removal")
    parser.add_argument("--category", type=str, default="", help="Category to purge (e.g. oled, anime)")
    parser.add_argument("--dry-run", action="store_true", help="Simulate without deleting")
    args = parser.parse_args()

    catalog = load_catalog()

    if args.action == "update-md":
        update_catalog_md(catalog)
        return

    if args.action == "list":
        update_catalog_md(catalog)
        summary_md = generate_catalog_markdown(catalog, for_github_summary=True)
        write_to_step_summary(summary_md)
        print(f"Catálogo listado com sucesso ({len(catalog.get('wallpapers', []))} itens).")
        return

    if args.action == "remove":
        raw_ids = [i.strip() for i in args.ids.replace(",", " ").split() if i.strip()]
        ids_set = set(raw_ids)

        if not ids_set and not args.keyword and (not args.category or args.category == "none"):
            print("Erro: Para remover, informe ao menos um ID (--ids), palavra-chave (--keyword) ou categoria (--category).")
            write_to_step_summary("### ⚠️ Erro: Nenhum critério de remoção foi informado (IDs, palavra-chave ou categoria).")
            sys.exit(1)

        remove_wallpapers(
            catalog=catalog,
            ids_to_remove=ids_set,
            keyword=args.keyword,
            category=args.category,
            dry_run=args.dry_run,
        )


if __name__ == "__main__":
    main()
