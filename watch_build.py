#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later

import sys
import os
import time
import json
import subprocess
import shutil
import re
from pathlib import Path

# ANSI colors
C_RESET = "\033[0m"
C_BOLD = "\033[1m"
C_DIM = "\033[2m"
C_RED = "\033[1;31m"
C_GREEN = "\033[1;32m"
C_YELLOW = "\033[1;33m"
C_BLUE = "\033[1;34m"
C_MAGENTA = "\033[1;35m"
C_CYAN = "\033[1;36m"
C_WHITE = "\033[1;37m"

def run_cmd(cmd, capture=True, check=False):
    """Run shell command and return stdout/stderr."""
    try:
        res = subprocess.run(
            cmd,
            shell=True,
            stdout=subprocess.PIPE if capture else None,
            stderr=subprocess.PIPE if capture else None,
            text=True,
            check=check,
        )
        return res.returncode, res.stdout.strip() if res.stdout else "", res.stderr.strip() if res.stderr else ""
    except Exception as e:
        return -1, "", str(e)

def get_repo():
    """Extract owner/repo from git remote."""
    _, out, _ = run_cmd("git remote get-url origin")
    if out:
        # Match git@github.com:owner/repo.git or https://github.com/owner/repo.git
        m = re.search(r"github\.com[:/]([^/]+/[^/\.]+)", out)
        if m:
            return m.group(1).replace(".git", "")
    return "S-Marcos-S/viagra-launcher"

def get_head_commit():
    """Get current HEAD commit SHA."""
    _, out, _ = run_cmd("git rev-parse HEAD")
    return out

def get_branch():
    """Get current git branch name."""
    _, out, _ = run_cmd("git rev-parse --abbrev-ref HEAD")
    return out or "main"

def clean_spinners(text):
    """Remove spinner characters and ANSI control sequences if needed."""
    return re.sub(r"[\u2800-\u28ff]", "", text).strip()

def find_run_for_commit(repo, commit_sha, max_wait_seconds=45):
    """Poll for a GitHub Actions workflow run matching commit_sha."""
    start_time = time.time()
    attempt = 1
    short_sha = commit_sha[:7] if commit_sha else ""

    print(f"{C_CYAN}🔍 Procurando execução do workflow para o commit {C_BOLD}{short_sha}{C_RESET}...")

    while time.time() - start_time < max_wait_seconds:
        cmd = f"gh run list -R {repo} -L 10 --json databaseId,status,conclusion,headSha,workflowName,createdAt,url"
        code, out, _ = run_cmd(cmd)
        if code == 0 and out:
            try:
                cleaned_out = clean_spinners(out)
                # Find valid JSON array inside output
                json_start = cleaned_out.find("[")
                if json_start != -1:
                    runs = json.loads(cleaned_out[json_start:])
                    for r in runs:
                        if r.get("headSha") == commit_sha:
                            return r.get("databaseId")
            except Exception:
                pass

        sys.stdout.write(f"\r{C_YELLOW}⏳ Aguardando GitHub Actions iniciar a build (tentativa {attempt})...{C_RESET}")
        sys.stdout.flush()
        time.sleep(3)
        attempt += 1

    print()
    # Fallback to the latest run
    cmd = f"gh run list -R {repo} -L 1 --json databaseId"
    code, out, _ = run_cmd(cmd)
    if code == 0 and out:
        try:
            cleaned_out = clean_spinners(out)
            json_start = cleaned_out.find("[")
            if json_start != -1:
                runs = json.loads(cleaned_out[json_start:])
                if runs:
                    latest_id = runs[0].get("databaseId")
                    print(f"{C_YELLOW}⚠️  Build específica do commit não encontrada; acompanhando a última build registrada: {latest_id}{C_RESET}")
                    return latest_id
        except Exception:
            pass

    return None

def get_run_details(repo, run_id):
    """Fetch status and jobs of a workflow run."""
    cmd = f"gh run view {run_id} -R {repo} --json status,conclusion,jobs,name,url,startedAt"
    code, out, err = run_cmd(cmd)
    if code != 0 or not out:
        return None
    try:
        cleaned_out = clean_spinners(out)
        json_start = cleaned_out.find("{")
        if json_start != -1:
            return json.loads(cleaned_out[json_start:])
    except Exception:
        return None
    return None

def clear_screen():
    sys.stdout.write("\033[2J\033[H")
    sys.stdout.flush()

def format_duration(start_iso):
    """Compute elapsed time in minutes and seconds from ISO timestamp."""
    try:
        # Format: 2026-09-11T02:06:07Z
        from datetime import datetime, timezone
        st = datetime.fromisoformat(start_iso.replace("Z", "+00:00"))
        now = datetime.now(timezone.utc)
        elapsed = int((now - st).total_seconds())
        m, s = divmod(max(0, elapsed), 60)
        return f"{m}m {s:02d}s"
    except Exception:
        return ""

def render_progress(run_data, repo):
    """Print visually pleasing status dashboard."""
    clear_screen()
    run_id = run_data.get("databaseId") or run_data.get("url", "").split("/")[-1]
    workflow_name = run_data.get("name", "Build APK")
    status = run_data.get("status", "unknown").upper()
    conclusion = (run_data.get("conclusion") or "").upper()
    url = run_data.get("url", "")
    started_at = run_data.get("startedAt", "")
    elapsed = format_duration(started_at) if started_at else ""

    print(f"{C_BOLD}{C_BLUE}=================================================================={C_RESET}")
    print(f"{C_BOLD}{C_CYAN}🚀 GITHUB ACTIONS — ACOMPANHAMENTO DE BUILD{C_RESET}")
    print(f"{C_BOLD}{C_BLUE}=================================================================={C_RESET}")
    print(f"{C_WHITE}📦 Repositório:{C_RESET}  {repo}")
    print(f"{C_WHITE}🆔 Run ID:{C_RESET}       {run_id}")
    print(f"{C_WHITE}📋 Workflow:{C_RESET}     {workflow_name}")
    if elapsed:
        print(f"{C_WHITE}⏱️  Tempo:{C_RESET}        {elapsed}")
    
    status_display = status
    if status == "COMPLETED":
        if conclusion == "SUCCESS":
            status_display = f"{C_GREEN}CONCLUÍDO COM SUCESSO (SUCCESS){C_RESET}"
        elif conclusion == "FAILURE":
            status_display = f"{C_RED}FALHOU (FAILURE){C_RESET}"
        else:
            status_display = f"{C_YELLOW}{conclusion}{C_RESET}"
    else:
        status_display = f"{C_YELLOW}EM ANDAMENTO ({status}){C_RESET}"
    print(f"{C_WHITE}⚡ Status:{C_RESET}       {status_display}")
    print(f"{C_DIM}🔗 {url}{C_RESET}")
    print(f"{C_BOLD}{C_BLUE}------------------------------------------------------------------{C_RESET}")
    print(f"{C_BOLD}JOBS & ETAPAS:{C_RESET}")

    jobs = run_data.get("jobs", [])
    for job in jobs:
        j_name = job.get("name", "Job")
        j_status = job.get("status", "")
        j_conclusion = job.get("conclusion") or ""
        
        if j_status == "completed":
            icon = f"{C_GREEN}✓{C_RESET}" if j_conclusion == "success" else f"{C_RED}✕{C_RESET}"
            j_tag = f"{C_GREEN}Sucesso{C_RESET}" if j_conclusion == "success" else f"{C_RED}Falhou{C_RESET}"
        elif j_status == "in_progress":
            icon = f"{C_YELLOW}⚙️{C_RESET}"
            j_tag = f"{C_YELLOW}Executando...{C_RESET}"
        else:
            icon = f"{C_DIM}⏳{C_RESET}"
            j_tag = f"{C_DIM}Na fila{C_RESET}"

        print(f"\n  {icon} {C_BOLD}{j_name}{C_RESET} [{j_tag}]")

        steps = job.get("steps", [])
        for step in steps:
            s_name = step.get("name", "")
            s_status = step.get("status", "")
            s_conclusion = step.get("conclusion") or ""

            if s_status == "completed":
                if s_conclusion == "success":
                    s_icon = f"{C_GREEN}✓{C_RESET}"
                elif s_conclusion == "skipped":
                    s_icon = f"{C_DIM}↷{C_RESET}"
                else:
                    s_icon = f"{C_RED}✕{C_RESET}"
            elif s_status == "in_progress":
                s_icon = f"{C_YELLOW}▶{C_RESET}"
            else:
                s_icon = f"{C_DIM}·{C_RESET}"

            print(f"      {s_icon} {s_name}")

    print(f"\n{C_BOLD}{C_BLUE}=================================================================={C_RESET}")

def download_and_install_apk(repo, run_id):
    """Download the release APK artifact and install using root."""
    print(f"\n{C_BOLD}{C_CYAN}📥 Baixando APK Release gerado na build...{C_RESET}")

    tmp_dir = Path("/tmp") / f"vl_apk_{int(time.time())}"
    tmp_dir.mkdir(parents=True, exist_ok=True)

    # 1. Tenta baixar via artefato de release específico da run atual
    print(f"{C_DIM}Baixando artefato de release da execução {run_id}...{C_RESET}")
    cmd = f"gh run download {run_id} -R {repo} -n viagara-launcher-release -D '{tmp_dir}'"
    code, _, err = run_cmd(cmd)

    apk_file = None
    if code == 0:
        apks = list(tmp_dir.glob("**/*release*.apk"))
        if apks:
            apk_file = apks[0]

    # 2. Se não encontrou o artefato isolado, tenta baixar apenas o APK release da release 'latest'
    if not apk_file:
        print(f"{C_YELLOW}Artefato direto de release não encontrado, baixando da release 'latest'...{C_RESET}")
        cmd = f"gh release download latest -R {repo} -p '*release*.apk' -D '{tmp_dir}' --clobber"
        code, _, _ = run_cmd(cmd)
        if code == 0:
            apks = list(tmp_dir.glob("**/*release*.apk"))
            if apks:
                apk_file = apks[0]

    # 3. Fallback para execuções legadas com nomes anteriores de artefato
    if not apk_file:
        for legacy_name in ["viagra-launcher-release", "victoria-launcher-release", "victoria-launcher-apks"]:
            cmd = f"gh run download {run_id} -R {repo} -n {legacy_name} -D '{tmp_dir}'"
            code, _, _ = run_cmd(cmd)
            if code == 0:
                apks = list(tmp_dir.glob("**/*release*.apk"))
                if apks:
                    apk_file = apks[0]
                    break
            # Remove qualquer APK de debug que possa ter vindo junto
            for dbg in tmp_dir.glob("**/*debug*.apk"):
                try:
                    dbg.unlink()
                except Exception:
                    pass

    if not apk_file or not apk_file.is_file():
        print(f"{C_RED}❌ Não foi possível encontrar o arquivo APK de release baixado.{C_RESET}")
        shutil.rmtree(tmp_dir, ignore_errors=True)
        return False

    size_mb = apk_file.stat().st_size / (1024 * 1024)
    print(f"{C_GREEN}✓ APK encontrado:{C_RESET} {apk_file.name} ({size_mb:.2f} MB)")

    # 3. Salva uma cópia na pasta de Downloads do dispositivo
    download_dirs = [
        Path("/storage/emulated/0/Download"),
        Path("/sdcard/Download"),
        Path.home() / "storage" / "downloads",
    ]
    saved_download_path = None
    for d in download_dirs:
        try:
            if d.exists():
                dest = d / "viagara-launcher-release.apk"
                shutil.copy2(str(apk_file), str(dest))
                saved_download_path = dest
                break
        except Exception:
            continue

    if saved_download_path:
        print(f"\n{C_BOLD}{C_GREEN}📁 APK salvo na pasta Downloads:{C_RESET} {saved_download_path}")

    # 4. Tenta instalação automática via root se pm estiver disponível
    dest_apk = "/data/local/tmp/viagara-launcher-release.apk"
    print(f"{C_CYAN}📲 Tentando instalação automática via Root...{C_RESET}")
    run_cmd(f"su -c \"cp '{apk_file.resolve()}' '{dest_apk}' && chmod 644 '{dest_apk}'\"")

    install_code, install_out, install_err = run_cmd(f"su -c \"pm install -r -d '{dest_apk}'\"")
    combined_output = f"{install_out}\n{install_err}".strip()
    run_cmd(f"su -c \"rm -f '{dest_apk}'\"")
    shutil.rmtree(tmp_dir, ignore_errors=True)

    if "Success" in combined_output:
        print(f"\n{C_BOLD}{C_GREEN}=================================================================={C_RESET}")
        print(f"{C_BOLD}{C_GREEN}🎉 VIAGARA LAUNCHER INSTALADO COM SUCESSO VIA ROOT!{C_RESET}")
        print(f"{C_BOLD}{C_GREEN}=================================================================={C_RESET}")
        print(f"{C_WHITE}A versão mais recente foi aplicada no seu dispositivo.{C_RESET}")
        return True
    else:
        print(f"\n{C_BOLD}{C_GREEN}=================================================================={C_RESET}")
        print(f"{C_BOLD}{C_GREEN}✓ APK PRONTO NA PASTA DE DOWNLOADS!{C_RESET}")
        print(f"{C_BOLD}{C_GREEN}=================================================================={C_RESET}")
        if saved_download_path:
            print(f"Local: {C_BOLD}{C_WHITE}{saved_download_path}{C_RESET}")
        print("Basta abrir seu gerenciador de arquivos / notificações e tocar no APK para instalar.")
        return True

def show_error_summary(repo, run_id, run_data):
    """Display error summary and failed logs."""
    print(f"\n{C_BOLD}{C_RED}=================================================================={C_RESET}")
    print(f"{C_BOLD}{C_RED}❌ A BUILD NO GITHUB ACTIONS FALHOU!{C_RESET}")
    print(f"{C_BOLD}{C_RED}=================================================================={C_RESET}")

    jobs = run_data.get("jobs", [])
    failed_job_ids = []
    build_apk_job_succeeded = False

    for job in jobs:
        j_name = job.get("name", "")
        j_conclusion = job.get("conclusion") or ""
        j_id = job.get("databaseId")

        if j_name == "Build APKs" and j_conclusion == "success":
            build_apk_job_succeeded = True

        if j_conclusion == "failure":
            failed_job_ids.append((j_name, j_id))
            print(f"\n{C_RED}• Job '{j_name}' falhou.{C_RESET}")
            for s in job.get("steps", []):
                if s.get("conclusion") == "failure":
                    print(f"  └─ Etapa com falha: {C_BOLD}{s.get('name')}{C_RESET}")

    print(f"\n{C_YELLOW}--- Resumo dos Logs de Erro ---{C_RESET}")
    cmd = f"gh run view {run_id} -R {repo} --log-failed"
    _, log_out, _ = run_cmd(cmd)
    cleaned_log = clean_spinners(log_out)

    if cleaned_log:
        lines = [l for l in cleaned_log.split("\n") if l.strip()]
        # Mostra as últimas 35 linhas mais relevantes
        display_lines = lines[-35:]
        for line in display_lines:
            print(f"  {line}")
    else:
        print("  (Logs detalhados disponíveis na interface web do GitHub Actions)")

    print(f"\n{C_CYAN}🔗 Ver build completa no GitHub:{C_RESET} {run_data.get('url')}")

    # Se a compilação do APK tiver dado certo mesmo com falha em testes:
    if build_apk_job_succeeded:
        print(f"\n{C_YELLOW}💡 O job de compilação do APK ('Build APKs') foi concluído com êxito!{C_RESET}")
        try:
            choice = input(f"{C_BOLD}Deseja baixar e instalar o APK gerado mesmo com o erro nos testes? [S/n]: {C_RESET}")
            if choice.strip().lower() in ("", "s", "sim", "y", "yes"):
                download_and_install_apk(repo, run_id)
        except (KeyboardInterrupt, EOFError):
            pass

def main():
    repo = get_repo()
    head_commit = get_head_commit()
    branch = get_branch()

    print(f"{C_BOLD}{C_CYAN}Viagara Launcher — Monitor de Build & Auto Instalador{C_RESET}")
    print(f"Repositório: {C_WHITE}{repo}{C_RESET} | Branch: {C_WHITE}{branch}{C_RESET}\n")

    # Verifica status git local
    _, git_status, _ = run_cmd("git status --porcelain")
    if git_status:
        print(f"{C_YELLOW}⚠️  Atenção: existem arquivos modificados localmente ainda não commitados.{C_RESET}")

    _, unpushed, _ = run_cmd(f"git log origin/{branch}..HEAD --oneline 2>/dev/null")
    if unpushed:
        print(f"{C_YELLOW}⚠️  Atenção: existem commits locais que ainda não foram enviados com 'git push'.{C_RESET}")
        print(f"{C_DIM}{unpushed}{C_RESET}\n")

    if len(sys.argv) > 1 and sys.argv[1] in ("--help", "-h"):
        print("Uso: ./watch_build.sh [RUN_ID | --install-latest | --help]")
        print("Opções:")
        print("  (sem argumentos)   Acompanha a build do commit atual do branch local")
        print("  <RUN_ID>           Acompanha uma execução específica pelo seu ID")
        print("  --install-latest   Baixa e instala via root o APK da última release")
        print("  -h, --help         Exibe esta ajuda")
        sys.exit(0)

    run_id = None
    if len(sys.argv) > 1:
        arg = sys.argv[1]
        if arg.isdigit():
            run_id = arg
        elif arg == "--install-latest":
            # Baixar e salvar diretamente
            print(f"{C_CYAN}Baixando release 'latest'...{C_RESET}")
            download_and_install_apk(repo, "latest")
            return

    if not run_id:
        run_id = find_run_for_commit(repo, head_commit)

    if not run_id:
        print(f"{C_RED}❌ Nenhuma execução do workflow encontrada no repositório {repo}.{C_RESET}")
        sys.exit(1)

    print(f"{C_GREEN}✓ Acompanhando execução:{C_RESET} {run_id}\n")
    time.sleep(1)

    while True:
        details = get_run_details(repo, run_id)
        if not details:
            print(f"{C_YELLOW}Aguardando resposta da API do GitHub...{C_RESET}")
            time.sleep(4)
            continue

        render_progress(details, repo)

        status = details.get("status")
        conclusion = details.get("conclusion")

        if status == "completed":
            build_job = next((j for j in details.get("jobs", []) if "Build" in j.get("name", "")), None)
            if build_job and build_job.get("conclusion") == "skipped":
                print(f"\n{C_YELLOW}ℹ️  A compilação do APK foi pulada (skipped) porque o commit não continha um número de versão.{C_RESET}")
                print(f"{C_DIM}Para disparar uma nova build automática, inclua o número da versão na mensagem de commit (ex: v0.59.48).{C_RESET}")
            elif conclusion == "success":
                print(f"\n{C_GREEN}✓ Todas as etapas foram concluídas com sucesso!{C_RESET}")
                download_and_install_apk(repo, run_id)
            else:
                show_error_summary(repo, run_id, details)
            break

        time.sleep(4)

if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print(f"\n{C_YELLOW}Monitoramento interrompido pelo usuário.{C_RESET}")
        sys.exit(0)
