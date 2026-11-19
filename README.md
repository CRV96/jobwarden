<p align="center">
  <img src="docs/branding/social-preview.png" alt="JobWarden: the keeper of your job hunt" width="720">
</p>

<p align="center">
  <b>The keeper of your job hunt.</b><br>
  Save job postings from your browser in one click, and keep every application's story, from the first save to the
  offer. Self-hosted, in 8 languages, with the AI assistant you already have.
</p>

<p align="center">
  <a href="https://github.com/CRV96/jobwarden/actions/workflows/ci.yml"><img alt="Build" src="https://img.shields.io/github/actions/workflow/status/CRV96/jobwarden/ci.yml?branch=main&style=for-the-badge&logo=githubactions&logoColor=white&label=Build"></a>
  <a href="https://sonarcloud.io/summary/overall?id=CRV96_job_tracker"><img alt="Quality gate" src="https://img.shields.io/sonar/quality_gate/CRV96_job_tracker?server=https%3A%2F%2Fsonarcloud.io&style=for-the-badge&logo=sonarqubecloud&logoColor=white&label=Quality%20gate"></a>
  <a href="https://sonarcloud.io/component_measures?id=CRV96_job_tracker&metric=coverage"><img alt="Coverage" src="https://img.shields.io/sonar/coverage/CRV96_job_tracker?server=https%3A%2F%2Fsonarcloud.io&style=for-the-badge&logo=sonarqubecloud&logoColor=white&label=Coverage"></a>
  <a href="https://coderabbit.ai"><img alt="CodeRabbit reviews" src="https://img.shields.io/coderabbit/prs/github/CRV96/jobwarden?style=for-the-badge&logo=coderabbit&logoColor=white&labelColor=171717&color=FF570A&label=CodeRabbit%20reviews"></a>
  <a href="LICENSE.md"><img alt="MIT License" src="https://img.shields.io/github/license/CRV96/jobwarden?style=for-the-badge&logo=opensourceinitiative&logoColor=white&color=3DA639"></a>
  <a href="https://discord.gg/A4U2eVNck"><img alt="Discord" src="https://img.shields.io/discord/1570434652211118110?style=for-the-badge&logo=discord&logoColor=white&label=Discord&color=5865F2"></a>
</p>
<h3 align="center">Support project</h3>
<p align="center">
  <a href="https://www.buymeacoffee.com/robertciapa"><img alt="Buy me a coffee" src="https://img.shields.io/badge/Buy_me_a_coffee-FFDD00?style=for-the-badge&logo=buymeacoffee&logoColor=black"></a>
  <a href="https://revolut.me/robertcrv"><img alt="Support me on Revolut" src="https://img.shields.io/badge/Support_me_on_Revolut-191C1F?style=for-the-badge&logo=revolut&logoColor=white"></a>
</p>

<h4 align="center">Guides</h4>
<p align="center">
  <a href="https://github.com/CRV96/jobwarden/wiki/Installation">Install</a> ·
  <a href="https://github.com/CRV96/jobwarden/wiki/Getting-started">Get started</a> ·
  <a href="https://github.com/CRV96/jobwarden/wiki">Wiki</a> ·
  <a href="https://github.com/CRV96/jobwarden/wiki/Browser-extension">Browser extension</a> ·
  <a href="https://discord.gg/A4U2eVNck">Discord</a>
</p>

## What it does

- **Every job in one place.** Save postings from Chrome or Firefox with the
  [browser extension](https://github.com/CRV96/jobwarden/wiki/Browser-extension), and follow each one on a timeline, a pipeline and a
  calendar, with reminders, statistics and CSV import and export.
- **Never the same job twice.** Open a posting you've seen before and the extension says so at once: ★ bookmarked,
  ✓ applied, on its toolbar icon and in its popup.
- **Finds jobs for you.** Your AI assistant searches for jobs that fit your CV, the watchlist reads the job boards of the
  companies you'd work for, and the Search Builder writes the queries for Google, LinkedIn, Reddit and X.
- **Applies better.** A CV tailored to each posting, cover letters, messages to the people hiring, a fit score,
  interview prep from your own stories, and Auto-apply in your own browser.
- **Reads your inbox.** Recruiters' replies become updates of the right job: rejections, interviews, offers.
- **Weighs offers.** Your offers side by side, what the market pays from three sources, and a brief to negotiate.
- **Yours.** It runs on your computer, with your data in your own PostgreSQL. Its AI runs on Claude Code or Codex, on
  your own plan: JobWarden needs no API key, and sends nothing anywhere.

## Quick start

With Docker ([how to install it](https://github.com/CRV96/jobwarden/wiki/Installing-Docker), on a Mac, Windows, Linux, a
Raspberry Pi or a NAS), in a folder for JobWarden:

```bash
curl -L -o compose.yaml https://github.com/CRV96/jobwarden/releases/latest/download/compose.yaml
docker compose up -d
```

and open <http://localhost:9273>. On Windows, in PowerShell, write `curl.exe` instead of `curl`.
[Installation](https://github.com/CRV96/jobwarden/wiki/Installation) has the other ways, from the sources included;
[Getting started](https://github.com/CRV96/jobwarden/wiki/Getting-started) the five minutes after.

## Documentation

Everything is in the **[wiki](https://github.com/CRV96/jobwarden/wiki)**:

| Run it | Use it | Build it |
|---|---|---|
| [Installing Docker](https://github.com/CRV96/jobwarden/wiki/Installing-Docker) | [Tracking jobs](https://github.com/CRV96/jobwarden/wiki/Tracking-jobs) | [What's what](https://github.com/CRV96/jobwarden/wiki/Architecture) |
| [Installation](https://github.com/CRV96/jobwarden/wiki/Installation) | [Finding jobs](https://github.com/CRV96/jobwarden/wiki/Finding-jobs) | [Development](https://github.com/CRV96/jobwarden/wiki/Development) |
| [Getting started](https://github.com/CRV96/jobwarden/wiki/Getting-started) | [Your CV and applying](https://github.com/CRV96/jobwarden/wiki/CV-and-applying) | [Contributing](CONTRIBUTING.md) |
| [Updating](https://github.com/CRV96/jobwarden/wiki/Updating) | [Offers and salaries](https://github.com/CRV96/jobwarden/wiki/Offers-and-salaries) | [Security](SECURITY.md) |
| [Configuration](https://github.com/CRV96/jobwarden/wiki/Configuration) | [AI assistant](https://github.com/CRV96/jobwarden/wiki/AI-assistant) | [Changelog](CHANGELOG.md) |
| [Operations](https://github.com/CRV96/jobwarden/wiki/Operations) | [Browser extension](https://github.com/CRV96/jobwarden/wiki/Browser-extension) | |

The wiki's pages live in [docs/wiki](docs/wiki), so they change in pull requests with the code.

## Code quality

<p align="center">
  <a href="https://sonarcloud.io/summary/overall?id=CRV96_job_tracker"><img alt="SonarQube Cloud quality gate" src="https://sonarcloud.io/api/project_badges/quality_gate?project=CRV96_job_tracker" width="141"></a>
</p>

<p align="center">
  <a href="https://sonarcloud.io/component_measures?id=CRV96_job_tracker&metric=security_rating"><img alt="Security rating" src="https://sonarcloud.io/api/project_badges/measure?project=CRV96_job_tracker&metric=security_rating" width="127"></a>
  <a href="https://sonarcloud.io/project/issues?id=CRV96_job_tracker&impactSoftwareQualities=SECURITY"><img alt="Vulnerabilities" src="https://sonarcloud.io/api/project_badges/measure?project=CRV96_job_tracker&metric=vulnerabilities" width="171"></a>
  <a href="https://sonarcloud.io/component_measures?id=CRV96_job_tracker&metric=sqale_rating"><img alt="Maintainability rating" src="https://sonarcloud.io/api/project_badges/measure?project=CRV96_job_tracker&metric=sqale_rating" width="180"></a>
</p>
<p align="center">
  <a href="https://sonarcloud.io/component_measures?id=CRV96_job_tracker&metric=coverage"><img alt="Coverage" src="https://sonarcloud.io/api/project_badges/measure?project=CRV96_job_tracker&metric=coverage" width="177"></a>
  <a href="https://sonarcloud.io/component_measures?id=CRV96_job_tracker&metric=duplicated_lines_density"><img alt="Duplicated lines" src="https://sonarcloud.io/api/project_badges/measure?project=CRV96_job_tracker&metric=duplicated_lines_density" width="216"></a>
  <a href="https://sonarcloud.io/component_measures?id=CRV96_job_tracker&metric=ncloc"><img alt="Lines of code" src="https://sonarcloud.io/api/project_badges/measure?project=CRV96_job_tracker&metric=ncloc" width="181"></a>
</p>

Every pull request is checked before it merges: [SonarQube Cloud](https://sonarcloud.io/summary/overall?id=CRV96_job_tracker)'s
quality gate on the code and what the tests cover, CodeQL's code scanning, the dependency review, and a
[CodeRabbit](https://coderabbit.ai) review. [SECURITY.md](SECURITY.md#how-the-project-is-checked) has the rest.

## Community

- **Questions, and what's being worked on:** the [Discord server](https://discord.gg/A4U2eVNck).
- **Ideas:** the [Ideas discussions](https://github.com/CRV96/jobwarden/discussions/categories/ideas), where others vote
  for them. **Suggest a feature**, in the app's profile menu, opens one with your version filled in.
- **Bugs:** [GitHub Issues](https://github.com/CRV96/jobwarden/issues/new/choose), with the steps to reproduce.
- **Vulnerabilities:** in private, as [SECURITY.md](SECURITY.md) says.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). Contributions require agreeing to the Contributor License Agreement described
there. To talk a change over first, ask on [Discord](https://discord.gg/A4U2eVNck).

## License

[MIT](LICENSE.md): use it, change it and share it, as long as the copyright notice and the license stay with it. It
comes without any warranty.
