# 微信/企微域名校验文件

这个目录用来放各平台后台下载的 `WW_verify_xxx.txt` / `MP_verify_xxx.txt`。

部署流程：
1. 在企微/微信公众平台后台点"下载校验文件"
2. 把文件直接丢到本目录
3. `cd deploy && ./scripts/deploy.sh`（重建 nginx 镜像后会一并拷进去）
4. 在平台后台点"已上传 → 验证"

nginx 路由在 `deploy/nginx/conf.d/finhub.tax.conf` 里：
访问 `https://finhub.tax/WW_verify_xxx.txt` 时直接命中本目录中的同名文件。
