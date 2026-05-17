# WeCom BFT 小程序端

S9 已将该目录升级为学员端小程序页面工程，覆盖课程、登录授权、下单支付、订单、学习、退款、发票、物流摘要和接口缺口页。

## 本地打开

1. 先执行 `pnpm --filter @wecom-bft/miniprogram build`
2. 微信开发者工具导入 `frontend/miniprogram`，或直接导入 `frontend/miniprogram/dist`
3. 根 `project.config.json` 的 `miniprogramRoot` 指向 `dist/`；`dist/project.config.json` 指向 `./`
4. 默认 API 地址在 `src/app.ts`：`http://localhost:8080`

## 构建产物

执行构建后会生成 `frontend/miniprogram/dist/`。构建脚本会把 `src/**/*.ts` 编译为同路径 `.js`，并复制 `.json/.wxml/.wxss` 等小程序静态文件；`project.config.json` 的 `miniprogramRoot` 会改为 `./`，微信开发者工具可直接导入 `dist/`。

## 验证

```bash
pnpm --filter @wecom-bft/miniprogram test
pnpm --filter @wecom-bft/miniprogram build
```

根命令 `pnpm frontend:test` 和 `pnpm build` 也会覆盖该小程序包。
