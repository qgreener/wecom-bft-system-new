# 构建并打包所有 H5/admin 前端到一个 nginx 静态文件镜像
# Build context = 仓库根目录
FROM node:22-alpine AS build
WORKDIR /workspace

# corepack 启用 pnpm（根 package.json 的 packageManager 已锁定版本）
RUN corepack enable

# 复制 pnpm 元数据先装依赖（利用 docker layer cache）
COPY package.json pnpm-workspace.yaml pnpm-lock.yaml ./
COPY frontend/shared/package.json ./frontend/shared/
COPY frontend/admin/package.json ./frontend/admin/
COPY frontend/h5/lead/package.json ./frontend/h5/lead/
COPY frontend/h5/supplier/package.json ./frontend/h5/supplier/
COPY frontend/h5/wecom-sidebar/package.json ./frontend/h5/wecom-sidebar/
RUN pnpm install --frozen-lockfile --config.dangerouslyAllowAllBuilds=true

# 复制实际源码
COPY frontend/shared ./frontend/shared
COPY frontend/admin ./frontend/admin
COPY frontend/h5 ./frontend/h5

# 构建 admin + 三个 H5
RUN pnpm --filter @wecom-bft/admin build \
    && pnpm --filter @wecom-bft/lead-h5 build \
    && pnpm --filter @wecom-bft/supplier-h5 build \
    && pnpm --filter @wecom-bft/wecom-sidebar-h5 build

# nginx 阶段：把构建产物拷到 /usr/share/nginx/html/
FROM nginx:1.27-alpine
COPY --from=build /workspace/frontend/admin/dist /usr/share/nginx/html/admin
COPY --from=build /workspace/frontend/h5/lead/dist /usr/share/nginx/html/h5/lead
COPY --from=build /workspace/frontend/h5/supplier/dist /usr/share/nginx/html/h5/supplier
COPY --from=build /workspace/frontend/h5/wecom-sidebar/dist /usr/share/nginx/html/h5/wecom-sidebar

# 微信/企微域名校验文件（可选）：把 WW_verify_xxx.txt 放到 deploy/nginx/verify/
# 该目录如不存在则什么都不会拷贝，nginx 路由会 404 不影响其他流量
COPY deploy/nginx/verify/ /usr/share/nginx/html/verify/

# 默认 nginx 配置由 docker-compose volume mount 注入
EXPOSE 80 443
