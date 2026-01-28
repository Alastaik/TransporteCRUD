FROM node:18-alpine

# Instala Chromium e dependências necessárias para whatsapp-web.js
RUN apk add --no-cache \
    chromium \
    nss \
    freetype \
    harfbuzz \
    ca-certificates \
    ttf-freefont \
    git

# Define variável de ambiente para Puppeteer usar Chromium instalado
ENV PUPPETEER_SKIP_CHROMIUM_DOWNLOAD=true \
    PUPPETEER_EXECUTABLE_PATH=/usr/bin/chromium-browser

# Diretório de trabalho
WORKDIR /app

# Copia package.json e package-lock.json
COPY package*.json ./

# Instala dependências
RUN npm install --production

# Copia código da aplicação
COPY server.js ./

# Cria diretório para sessões do WhatsApp
RUN mkdir -p /app/whatsapp-session && \
    chown -R node:node /app

# Usa usuário não-root
USER node

# Expõe porta
EXPOSE 3000

# Comando para iniciar
CMD ["node", "server.js"]
