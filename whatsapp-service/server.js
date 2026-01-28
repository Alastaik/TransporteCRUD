const express = require('express');
const { Client, LocalAuth } = require('whatsapp-web.js');
const qrcode = require('qrcode');
const cors = require('cors');

const app = express();
const PORT = 3000;

// Middleware
app.use(cors());
app.use(express.json());

// Estado global
let client = null;
let qrCodeData = null;
let isReady = false;
let isConnecting = false;

// Inicializa o cliente WhatsApp
function initializeClient() {
    console.log('🔧 Inicializando cliente WhatsApp Web...');

    client = new Client({
        authStrategy: new LocalAuth({
            clientId: 'logistica-bot',
            dataPath: './whatsapp-session'
        }),
        puppeteer: {
            headless: true,
            args: [
                '--no-sandbox',
                '--disable-setuid-sandbox',
                '--disable-dev-shm-usage',
                '--disable-accelerated-2d-canvas',
                '--no-first-run',
                '--no-zygote',
                '--disable-gpu'
            ]
        }
    });

    // Event: QR Code recebido
    client.on('qr', async (qr) => {
        console.log('📱 QR Code gerado!');
        try {
            // Converte QR Code para Base64
            qrCodeData = await qrcode.toDataURL(qr);
            console.log('✅ QR Code convertido para Base64');
        } catch (err) {
            console.error('❌ Erro ao gerar QR Code:', err);
        }
    });

    // Event: Cliente pronto
    client.on('ready', () => {
        console.log('✅ WhatsApp conectado e pronto!');
        isReady = true;
        isConnecting = false;
        qrCodeData = null; // Limpa QR Code após conectar
    });

    // Event: Autenticado
    client.on('authenticated', () => {
        console.log('🔐 WhatsApp autenticado');
        isConnecting = true;
    });

    // Event: Desconectado
    client.on('disconnected', (reason) => {
        console.log('⚠️ WhatsApp desconectado:', reason);
        isReady = false;
        isConnecting = false;
        qrCodeData = null;
    });

    // Event: Erro de autenticação
    client.on('auth_failure', (msg) => {
        console.error('❌ Falha na autenticação:', msg);
        isReady = false;
        isConnecting = false;
    });

    // Event: Debug - Todas as mensagens (incluindo próprias)
    client.on('message_create', (message) => {
        console.log('🔔 MESSAGE_CREATE EVENT:', {
            from: message.from,
            body: message.body,
            fromMe: message.fromMe,
            type: message.type
        });
    });

    // Event: Mensagem recebida
    client.on('message', async (message) => {
        console.log('------------------------------------------------');
        console.log(`📩 MENSAGEM RECEBIDA!`);
        console.log(`FROM: ${message.from}`);
        console.log(`BODY: ${message.body}`);
        console.log(`TYPE: ${message.type}`);
        console.log(`FROM_ME: ${message.fromMe}`);
        console.log('------------------------------------------------');

        // Ignora mensagens de grupos e broadcasts
        if (message.from.includes('@g.us') || message.from.includes('@broadcast')) {
            console.log('⏭️ Mensagem de grupo/broadcast ignorada');
            return;
        }

        // Ignora mensagens próprias (COMENTADO PARA TESTES)
        // if (message.fromMe) {
        //     console.log('⏭️ Mensagem própria ignorada');
        //     return;
        // }

        try {
            // Extrai número do usuário (remove @c.us ou @lid)
            const userId = message.from.replace('@c.us', '').replace('@lid', '');

            // Pega nome do contato
            const contact = await message.getContact();
            const userName = contact.pushname || contact.name || contact.number || userId;

            console.log(`📱 Contato: ${userName} (${userId})`);

            console.log(`⏳ Buscando histórico de conversas...`);

            // Busca histórico (últimas 4 mensagens, filtradas por tempo)
            const chat = await message.getChat();
            const historyMessages = await chat.fetchMessages({ limit: 15 }); // Busca mais para filtrar

            // Filtra mensagens das últimas 8 horas
            const eightHoursAgo = Math.floor(Date.now() / 1000) - (8 * 60 * 60);
            const recentMessages = historyMessages
                .filter(msg => msg.timestamp >= eightHoursAgo)
                .slice(-4); // Pega apenas as últimas 4 mensagens recentes

            console.log(`📊 Histórico: ${recentMessages.length} mensagens (últimas 8h, max 4)`);

            // Formata histórico para o prompt com Timestamp
            let historyText = "";
            recentMessages.forEach(msg => {
                const role = msg.fromMe ? "Bot" : "User";
                const date = new Date(msg.timestamp * 1000);
                const time = date.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });
                historyText += `[${time} - ${role}]: ${msg.body}\n`;
            });

            console.log(`🔄 Enviando para IA (Spring Boot) com histórico...`);

            // Chama o Spring Boot para processar com IA e Contexto
            const fetch = (await import('node-fetch')).default;
            const response = await fetch('http://host.docker.internal:8082/api/chat/message', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({
                    userId: userId,
                    userName: userName, // NOVO: nome do contato
                    message: message.body,
                    history: historyText // Novo campo enviado
                })
            });

            if (!response.ok) {
                throw new Error(`HTTP ${response.status}: ${await response.text()}`);
            }

            const data = await response.json();

            if (data.success) {
                if (data.reply && data.reply.trim().length > 0) {
                    // Envia resposta apenas se houver texto
                    await client.sendMessage(message.from, data.reply);
                    console.log(`✅ Resposta IA enviada para ${message.from}`);
                } else {
                    // IA decidiu ficar em silêncio
                    console.log(`🤫 IA optou pelo silêncio para ${message.from}`);
                }
            } else {
                // Erro retornado pelo Spring Boot
                await client.sendMessage(message.from, 'Desculpe, houve um erro ao processar sua mensagem.');
                console.error('❌ Spring Boot retornou erro:', data);
            }

        } catch (error) {
            console.error('❌ Erro ao processar mensagem:', error);
            try {
                await client.sendMessage(message.from, 'Desculpe, estou com problemas técnicos. Tente novamente em alguns instantes.');
            } catch (sendError) {
                console.error('❌ Erro ao enviar mensagem de erro:', sendError);
            }
        }
    });

    // Inicializa o cliente
    client.initialize();
}

// ====== ENDPOINTS REST ======

// POST /session/start - Inicia sessão WhatsApp
app.post('/session/start', (req, res) => {
    console.log('🚀 POST /session/start - Iniciando sessão...');

    // VERIFICAÇÃO ROBUSTA: Se já existe cliente, não deixa iniciar de novo
    if (client) {
        console.log('⚠️ Cliente já existe. Ignorando solicitação de início.');

        let status = 'connecting';
        if (isReady) status = 'ready';

        return res.json({
            success: true,
            message: 'Sessão já está ativa ou inicializando',
            status: status
        });
    }

    try {
        initializeClient();
        res.json({
            success: true,
            message: 'Sessão iniciada. Use GET /session/qr para obter o QR Code.',
            status: 'connecting'
        });
    } catch (error) {
        console.error('❌ Erro ao iniciar sessão:', error);
        res.status(500).json({
            success: false,
            message: 'Erro ao iniciar sessão: ' + error.message
        });
    }
});

// GET /session/qr - Retorna QR Code
app.get('/session/qr', (req, res) => {
    console.log('📱 GET /session/qr - Solicitado QR Code');

    if (isReady) {
        return res.json({
            success: true,
            message: 'WhatsApp já está conectado',
            status: 'ready'
        });
    }

    if (!qrCodeData) {
        return res.status(404).json({
            success: false,
            message: 'QR Code não disponível. Inicie a sessão primeiro com POST /session/start',
            status: isConnecting ? 'connecting' : 'disconnected'
        });
    }

    res.json({
        success: true,
        qrcode: qrCodeData,
        status: 'connecting'
    });
});

// GET /session/status - Status da conexão
app.get('/session/status', (req, res) => {
    console.log('🔍 GET /session/status - Verificando status');

    let status = 'disconnected';
    if (isReady) status = 'ready';
    else if (isConnecting) status = 'connecting';

    res.json({
        success: true,
        status: status,
        hasQrCode: !!qrCodeData,
        isReady: isReady
    });
});

// POST /message/send - Envia mensagem
app.post('/message/send', async (req, res) => {
    const { number, message } = req.body;

    console.log(`💬 POST /message/send - Enviando para ${number}`);

    if (!isReady || !client) {
        return res.status(400).json({
            success: false,
            message: 'WhatsApp não está conectado'
        });
    }

    if (!number || !message) {
        return res.status(400).json({
            success: false,
            message: 'Parâmetros "number" e "message" são obrigatórios'
        });
    }

    try {
        // Formata número: remove caracteres especiais e adiciona @c.us
        const chatId = number.replace(/[^\d]/g, '') + '@c.us';

        await client.sendMessage(chatId, message);

        console.log(`✅ Mensagem enviada para ${number}`);
        res.json({
            success: true,
            message: 'Mensagem enviada com sucesso',
            to: number
        });
    } catch (error) {
        console.error('❌ Erro ao enviar mensagem:', error);
        res.status(500).json({
            success: false,
            message: 'Erro ao enviar mensagem: ' + error.message
        });
    }
});

// DELETE /session/logout - Desconecta sessão
app.delete('/session/logout', async (req, res) => {
    console.log('🔌 DELETE /session/logout - Desconectando...');

    if (!client) {
        return res.json({
            success: true,
            message: 'Nenhuma sessão ativa'
        });
    }

    try {
        await client.logout();
        await client.destroy();
        client = null;
        isReady = false;
        isConnecting = false;
        qrCodeData = null;

        console.log('✅ Sessão desconectada');
        res.json({
            success: true,
            message: 'Sessão desconectada com sucesso'
        });
    } catch (error) {
        console.error('❌ Erro ao desconectar:', error);
        res.status(500).json({
            success: false,
            message: 'Erro ao desconectar: ' + error.message
        });
    }
});

// Health check
app.get('/health', (req, res) => {
    res.json({
        success: true,
        service: 'whatsapp-service',
        status: 'running',
        whatsapp: isReady ? 'connected' : 'disconnected'
    });
});

// Inicia o servidor
app.listen(PORT, () => {
    console.log('==============================================');
    console.log('🚀 WhatsApp Service rodando na porta', PORT);
    console.log('==============================================');
    console.log('Endpoints disponíveis:');
    console.log('  POST   /session/start   - Iniciar sessão');
    console.log('  GET    /session/qr      - Obter QR Code');
    console.log('  GET    /session/status  - Status');
    console.log('  POST   /message/send    - Enviar mensagem');
    console.log('  DELETE /session/logout  - Desconectar');
    console.log('  GET    /health          - Health check');
    console.log('==============================================');
});

// Graceful shutdown
process.on('SIGINT', async () => {
    console.log('\n⚠️  Encerrando servidor...');
    if (client) {
        await client.destroy();
    }
    process.exit(0);
});
