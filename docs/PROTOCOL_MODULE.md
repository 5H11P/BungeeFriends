# Shared API Modules

`protocol` contains the bounded wire format shared by proxy and Spigot endpoints. API 3 uses protocol v2; every proxy and backend endpoint must be upgraded together.

`api-contract` defines shared query/action interfaces, immutable DTOs, player status, and pagination. Its Maven coordinate is `net.simplyrin.bungeefriends.api:BungeeFriends-API-Contract:${project.version}`.

`bungee-api` depends on `core`, `protocol`, and `api-contract`. `spigot-api` depends on `protocol` and `api-contract`. Both endpoint JARs shade the protocol and contract classes so those modules are build dependencies, not separately installed server plugins.

Run the module tests and full build from the repository root:

```sh
mvn clean test package
```
