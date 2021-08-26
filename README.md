# HAProxyDetector

This [BungeeCord](https://github.com/SpigotMC/BungeeCord/) (and now [Spigot](https://www.spigotmc.org/wiki/spigot/)) plugin enables proxied and direct connections both at the same time. More infomation about [HAProxy](https://www.haproxy.org/) and its uses can be found [here](https://github.com/MinelinkNetwork/BungeeProxy/blob/master/README.md).

> Note: `proxy_protocol` needs to be enabled in BC config for this plugin to work.

Older versions of BC can in theory use [BungeeProxy](https://github.com/MinelinkNetwork/BungeeProxy) in parallel with this plugin. This combination haven't been tested yet. Feedback is welcomed.

If errors like `NoClassDefFoundError: sun.misc.Unsafe`, `InaccessibleObjectException` and such are encountered, please add `--add-opens java.base/java.lang.invoke=ALL-UNNAMED` to JVM arguments.
