# Installing Docker

JobWarden runs in [Docker](https://www.docker.com/): one command starts it with its database, on any computer. Here is
how to get Docker on yours. Once it's in, go on with [Installation](Installation).

**Have it already?** In a terminal (Terminal on a Mac, PowerShell on Windows), `docker compose version` prints a version.

**What it takes:** JobWarden and its database use about 800 MB of memory and 1 GB of disk. Any computer from the last
few years has room, and so does a Raspberry Pi 4 or 5 with 4 GB.

## Mac

[Docker Desktop](https://www.docker.com/products/docker-desktop/) is free for personal use, as for small businesses
(fewer than 250 people and under $10 million a year), education and open-source projects. It runs on the current macOS
and the two before it, with 4 GB of memory.

1. Download Docker Desktop for your Mac's chip: **Apple silicon** (M1 and later) or **Intel**. The Apple menu →
   **About This Mac** says which.
2. Open the `.dmg`, drag **Docker** into **Applications**, and open it from there. Accept the terms; you can skip
   signing in.
3. Wait for the whale in the menu bar to say **Docker Desktop is running**.
4. In Docker Desktop's **Settings → General**, tick **Start Docker Desktop when you sign in**: JobWarden then comes back
   on its own after a restart.

[OrbStack](https://orbstack.dev) is a lighter alternative for Macs, also free for personal use: the same `docker`
commands work with it.

## Windows

Docker Desktop for Windows runs on Windows 10 (22H2) or Windows 11, 64-bit, with 8 GB of memory, on top of WSL 2,
Windows's own Linux.

1. Check that virtualization is on: **Task Manager → Performance → CPU** should say **Virtualization: Enabled**. If it
   doesn't, turn it on in your computer's BIOS or UEFI settings (often called Intel VT-x, AMD-V or SVM).
2. Install WSL 2: open **PowerShell as administrator**, run `wsl --install`, and restart when it's done.
3. Download [Docker Desktop for Windows](https://www.docker.com/products/docker-desktop/) and run the installer. Keep
   **Use WSL 2 instead of Hyper-V** ticked, and restart if it asks.
4. Open Docker Desktop, accept the terms, and wait until it says it's running. In its **Settings → General**, tick
   **Start Docker Desktop when you sign in**.

**In PowerShell, write `curl.exe`, not `curl`,** for the download in [Installation](Installation): in Windows
PowerShell, `curl` is another command, which doesn't understand `-L -o`.

```powershell
mkdir $HOME\jobwarden; cd $HOME\jobwarden
curl.exe -L -o compose.yaml https://github.com/CRV96/jobwarden/releases/latest/download/compose.yaml
docker compose up -d
```

## Linux

On a Linux computer or server, install **Docker Engine**, free and open source. Docker's install script sets it up,
with Compose, on Ubuntu, Debian, Fedora, Raspberry Pi OS and most others:

```bash
curl -fsSL https://get.docker.com -o get-docker.sh
sudo sh get-docker.sh
```

For a server you'll keep for years, Docker suggests its
[package repositories](https://docs.docker.com/engine/install/) instead, which update with the rest of the system.

Then, to run `docker` without `sudo`, add yourself to the `docker` group, and log out and back in (or run `newgrp
docker`):

```bash
sudo usermod -aG docker $USER
```

Being in the `docker` group is as good as having root on that computer, so add only yourself.

Docker starts with the computer on Debian and Ubuntu. On the others:

```bash
sudo systemctl enable --now docker.service containerd.service
```

## Raspberry Pi

A **Raspberry Pi 4 or 5** with the **64-bit** Raspberry Pi OS: follow [Linux](#linux) above. `uname -m` says
`aarch64` on a 64-bit system. A 32-bit system (`armv7l`) can't run JobWarden: Java 25 isn't made for it.

## NAS

A NAS runs JobWarden well: it's always on. It needs the Docker app of its system and the release's `compose.yaml`,
with one change: the port.

**Synology** (DSM 7.2 or later, on the models that have **Container Manager**: the Plus series, and some others; Synology's
[package page](https://www.synology.com/dsm/packages/ContainerManager) lists them):

1. In **Package Center**, install **Container Manager**.
2. In **File Station**, make a folder for JobWarden, e.g. `docker/jobwarden`.
3. In **Container Manager → Project → Create**: name it `jobwarden`, pick that folder as its path, and as its source
   upload the release's `compose.yaml` (or paste it in).
4. Change the port, as below, then **Next** and **Done**: it downloads the images and starts.

**QNAP** (Container Station), **Unraid** and **TrueNAS SCALE** run Compose files too: create an application (a stack,
a project) from `compose.yaml`, with the same change to the port.

**The port:** `compose.yaml` publishes JobWarden on its own computer only (`127.0.0.1:9273:9273`), and a NAS has no
browser of its own. To open it from your computers and phone, publish it on your network:

```yaml
    ports:
      - 9273:9273
```

Then open `http://<your NAS's address>:9273`, e.g. `http://192.168.1.20:9273` or `http://mynas.local:9273`.
**JobWarden has no sign-in:** everyone on that network can then read and change your job hunt. Do this on a network you
trust, at home, and never forward the port to the internet: [SECURITY.md](https://github.com/CRV96/jobwarden/blob/main/SECURITY.md#running-jobwarden-safely)
says more.

## Check it works

```bash
docker --version
docker compose version
docker run --rm hello-world
```

`hello-world` prints **Hello from Docker!** when everything is in place. Now [install JobWarden](Installation).

## When something's wrong

| What it says | What to do |
|---|---|
| `Cannot connect to the Docker daemon` | Docker isn't running: open Docker Desktop (or OrbStack), or on Linux `sudo systemctl start docker` |
| `permission denied while trying to connect to the Docker daemon socket` | On Linux, you're not in the `docker` group yet: see [Linux](#linux), then log out and back in |
| `no configuration file provided: not found` | `compose.yaml` isn't in this folder: `ls` (`dir` on Windows) should list it. Download it again, with `-o compose.yaml` |
| `no matching manifest for linux/arm/v7` | A 32-bit system: JobWarden needs a 64-bit one (see [Raspberry Pi](#raspberry-pi)) |
| `port is already allocated` | Something else uses 9273, e.g. another JobWarden. Stop it, or change the first number: `127.0.0.1:9274:9273`, then open `http://localhost:9274` |
| `A parameter cannot be found that matches parameter name 'L'`, in PowerShell | Write `curl.exe` instead of `curl` |
