# Security Policy

## Supported Versions

The current version on the `main` branch is supported.

## Reporting a Vulnerability

Do not publish vulnerability details in an open issue.

If GitHub private vulnerability reporting is enabled for this repository, use it. If it is not
available, open an issue without exploit details and ask the maintainer for a private channel to
share technical information.

Include:

- affected version or commit;
- brief risk summary;
- minimal reproduction steps, if they can be shared safely;
- expected impact;
- known workaround, if any.

This project handles a Redmine API key and can (when explicitly enabled) create and modify issues,
notes, time entries, and wiki pages through the account that owns the key, so reports about key
leaks, unintended write operations against the Redmine REST API, corruption of the stdio JSON-RPC
channel, and unintended exposure of private projects or issues are especially important.
