# HAProxyDetector

[![](https://img.shields.io/github/downloads/andylizi/haproxy-detector/total?style=for-the-badge)](https://github.com/andylizi/haproxy-detector/releases) [![](https://img.shields.io/github/license/andylizi/haproxy-detector?style=for-the-badge)](https://github.com/andylizi/haproxy-detector/blob/master/LICENSE) [![](https://img.shields.io/bstats/servers/12604?label=Spigot%20Servers&style=for-the-badge)](https://bstats.org/plugin/bukkit/HAProxyDetector/12604) [![](https://img.shields.io/bstats/servers/12605?label=BC%20Servers&style=for-the-badge)](https://bstats.org/plugin/bungeecord/HAProxyDetector/12605) [![](https://img.shields.io/bstats/servers/14442?label=Velocity%20Servers&style=for-the-badge)](https://bstats.org/plugin/velocity/HAProxyDetector/14442)

This [BungeeCord](https://github.com/SpigotMC/BungeeCord/) (and now [Spigot](https://www.spigotmc.org/wiki/spigot/)
and [Velocity](https://velocitypowered.com/)) plugin enables proxied and direct connections both at the same time. More
infomation about [HAProxy](https://www.haproxy.org/) and its uses can be
found [here](https://github.com/MinelinkNetwork/BungeeProxy/blob/master/README.md).

## Security Warning

Allowing both direct and proxied connections has significant security implications — a malicious player can access the
server through their own HAProxy instance, thus tricking the server into believing the connection is coming from a
fake IP.

To counter this, this plugin implements IP whitelisting. **By default, only proxied connections from `localhost` will be
allowed** (direct connections aren't affected). You can add the IP/domain of your trusted HAProxy instance by
editing `whitelist.conf`, which can be found under the plugin data folder.

<details>
    <summary>Details of the whitelist format</summary>

```
# List of allowed proxy IPs
#
# An empty whitelist will disallow all proxies.
# Each entry must be an valid IP address, domain name or CIDR.
# Domain names will be resolved only once at startup.
# Each domain can have multiple A/AAAA records, all of them will be allowed.
# CIDR prefixes are not allowed in domain names.

127.0.0.0/8
::1/128
```

If you want to disable the whitelist (which you should never do), you can do so by
putting this line verbatim, before any other entries:

```
YesIReallyWantToDisableWhitelistItsExtremelyDangerousButIKnowWhatIAmDoing!!!
```

</details>

## Platform-specific Notes

#### BungeeCord

`proxy_protocol` needs to be enabled in BC config for this plugin to work.

Older versions of BC can in theory use [BungeeProxy](https://github.com/MinelinkNetwork/BungeeProxy) in parallel
with this plugin, but it hasn't been tested yet. Feedback is welcomed.

#### Spigot and its derivatives

[ProtocolLib](https://github.com/dmulloy2/ProtocolLib) is a required dependency.
This plugin was developed using ProtocolLib v4.8.0; please try that version first if there are any errors.

#### Velocity

`haproxy-protocol` needs to be enabled in Velocity config for this plugin to work.

Versions older than 3.0 are not supported.

#### Java >= 9

If errors like `NoClassDefFoundError: sun.misc.Unsafe`, `InaccessibleObjectException` and such are encountered,
please add `--add-opens java.base/java.lang.invoke=ALL-UNNAMED` to JVM arguments.

#### Java >= 18

If errors like `IllegalAccessException: static final field has no write access` are encountered,
please upgrade the plugin to at least v3.0.2.

If you cannot upgrade for whatever reason, a temporary workaround would be to add
`-Djdk.reflect.useDirectMethodHandle=false` to JVM arguments.

Note that this argument will be removed in future Java releases.

## Metrics

This plugin uses [bStats](https://bStats.org) for metrics. It collects some basic information, like how many people
use this plugin and the total player count. You can opt out at any time by editing the config file under
`plugins/bStats/`.
