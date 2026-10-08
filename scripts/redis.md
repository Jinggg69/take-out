# 本地 Redis

本项目使用社区维护的 Redis 8.10.2 Windows x64 Cygwin 便携版，仅用于本地开发：
https://github.com/redis-windows/redis-windows/releases/tag/8.10.2

安装目录为 `tools/redis/`，已加入 Git 忽略规则。服务仅监听 `127.0.0.1:6379`，启用密码认证、AOF（每秒同步）和 RDB 快照。数据在 `tools/redis/data/`，日志在 `tools/redis/redis.log`。

在项目根目录运行：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\redis.ps1 start
powershell -ExecutionPolicy Bypass -File .\scripts\redis.ps1 status
powershell -ExecutionPolicy Bypass -File .\scripts\redis.ps1 cli INFO server
powershell -ExecutionPolicy Bypass -File .\scripts\redis.ps1 stop
```

启动脚本在后台运行 Redis；重启电脑后需要再次运行 `start`。此安装不注册 Windows 服务。

Spring Boot 2.7 使用 `spring.redis` 配置。随机生成的本地密码保存在被 Git 忽略的 `application-dev.yml` 中的 `sky.redis.password`，与本地 `redis.conf` 保持一致。可通过环境变量 `REDIS_HOST`、`REDIS_PORT`、`REDIS_PASSWORD`、`REDIS_DATABASE` 覆盖连接参数。查看密码请在本机打开上述配置文件；不要提交这些文件。

如果删除了本地安装目录，重新下载上述发行版的 `Redis-8.10.2-Windows-x64-cygwin.zip`，核对 SHA256 为 `6de5cc7f5adbf97b5928b13766383d4ad424626ef3d8b313ffff12d820ec6fc1`，解压到 `tools/redis/8.10.2/`。恢复自己的 `redis.conf` 和 `data/`；新安装则重新生成密码并同步到 `application-dev.yml`。
