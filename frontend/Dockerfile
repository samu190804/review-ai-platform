# --- Etapa 1: compilar ---------------------------------------------------------
# Necesita Node y todas las dependencias de desarrollo; nada de eso viaja a la imagen final.
# Variante Debian (slim) y no Alpine: algunos módulos nativos de Angular no traen versión para musl.
FROM node:24-slim AS build

WORKDIR /build

# 1) Dependencias. Se copian solo los archivos que las definen, para que esta capa se reutilice
#    mientras no cambien. "npm ci" instala exactamente lo que dice package-lock.json.
COPY package.json package-lock.json ./
RUN npm ci

# 2) Código. Es lo que más cambia, por eso va después.
COPY . .
RUN npm run build

# --- Etapa 2: servir -----------------------------------------------------------
# nginx sirve los archivos estáticos y reenvía /api al backend (ver nginx.conf).
# La variante "unprivileged" no corre como root y por eso escucha en el 8080 y no en el 80.
FROM nginxinc/nginx-unprivileged:alpine

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/dist/frontend/browser /usr/share/nginx/html

EXPOSE 8080

# 127.0.0.1 y no localhost: dentro de la imagen "localhost" se resuelve a IPv6 y nginx solo escucha en IPv4.
HEALTHCHECK --interval=15s --timeout=5s --start-period=10s --retries=3 \
    CMD wget --quiet --spider http://127.0.0.1:8080/ || exit 1
