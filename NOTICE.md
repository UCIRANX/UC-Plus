# Notices

Lumen is a standalone project that originated from zapret-kvn and contains modified code and components from it. Copyright and license notices are preserved below.

This project preserves copyright and attribution notices required by the
licenses of source materials and bundled third-party components.

Preserved copyright notices:

- Copyright (c) 2026 youtubediscord/zapret-kvn contributors
- Copyright (c) 2026 krambovic/Lumen contributors
- Copyright (c) 2013 Kevin Cernekee (libopenconnect Java binding, LibOpenConnect.java) — LGPL-2.1
- openconnect 9.21 (LGPL-2.1): https://www.infradead.org/openconnect/ — built by android/native/build-openconnect.sh
- OpenTunnel (AlieShayan, LGPL-2.1): the OpenConnect build layout and the OpenTunnel LGPL-2.1 text are in licenses/OPENTUNNEL-LGPL-2.1.txt

Relevant source and attribution links:

- https://github.com/youtubediscord/zapret-kvn
- https://github.com/krambovic/Lumen

Bundled third-party components keep their own licenses and notices:

- Xray-core: https://github.com/XTLS/Xray-core
- sing-box-extended: https://github.com/shtorm-7/sing-box-extended
- zapret/WinDivert bundle: see files under `zapret/`, including
  `zapret/windivert.filter/manual.md`.

Optional external helpers downloaded or used separately:

- droute 2.2.1 (GPL-3.0, bundled upstream binary distribution with GitHub updates): https://github.com/snowluwu/droute
  GPL-3.0 component used as a separate helper for Discord TCP/UDP SOCKS5
  proxying via Discord-local DLL loading and Squirrel updater hook. Its source
  code is not embedded into this repository.

- openconnect 9.21 (LGPL-2.1), OpenSSL 3.0.x (Apache-2.0), libxml2 (MIT) and lz4 (BSD-2-Clause), as upstream: built into libopenconnect.so by
  android/native/build-openconnect.sh. The Java binding LibOpenConnect.java is LGPL-2.1.
