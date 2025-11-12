# --- Build stage ---
FROM node:20-alpine AS build
WORKDIR /app

COPY ui/package.json package-lock.json* pnpm-lock.yaml* yarn.lock* ./
RUN npm ci || npm install
COPY ui .
RUN npm run build

# --- Runtime stage (nginx) ---
FROM nginx:1.27-alpine
LABEL org.opencontainers.image.title="heatwise-ui" \
      org.opencontainers.image.description="Heatwise UI static site" \
      org.opencontainers.image.licenses="Apache-2.0"

COPY --from=build /app/dist/ /usr/share/nginx/html/
COPY ui/public/config.json /usr/share/nginx/html/config.json

# Minimal security tweaks
RUN rm -f /etc/nginx/conf.d/default.conf && \
    printf 'server { listen 8080; server_name _; root /usr/share/nginx/html; index index.html; \
    location / { try_files $uri $uri/ /index.html; } }\n' > /etc/nginx/conf.d/site.conf

EXPOSE 8080
CMD ["nginx", "-g", "daemon off;"]