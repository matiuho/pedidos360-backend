# Despliegue en AWS Academy

Este despliegue usa una instancia EC2 con Docker Compose. Todos los microservicios,
MySQL, RabbitMQ y el frontend se ejecutan dentro de la instancia.

## 1. Crear la instancia

En AWS Academy/Learner Lab:

1. Inicia el laboratorio y abre **AWS Console**.
2. Crea una instancia EC2 Ubuntu 24.04, tipo `t3.medium` si está disponible.
3. En el security group permite:
   - TCP `22` desde tu IP.
   - TCP `80` desde Internet.
4. Guarda la IP pública de la instancia.

Conéctate por SSH y instala Docker:

```bash
sudo apt-get update
sudo apt-get install -y docker.io docker-compose-plugin git
sudo systemctl enable --now docker
sudo usermod -aG docker "$USER"
```

Cierra y vuelve a abrir la sesión SSH para que se aplique el grupo `docker`.

## 2. Copiar el proyecto

Puedes clonar ambos repositorios o copiar las dos carpetas manteniendo esta
estructura:

```text
Evaluacion N2/
├── pedidos360-backend/
└── pedidos360-frontend/
```

El `docker-compose.aws.yml` del backend usa `../pedidos360-frontend` como
contexto de build.

## 3. Configurar Entra ID y variables

En la App Registration del frontend crea una Redirect URI de tipo SPA con:

```text
http://IP_PUBLICA_EC2
```

En el backend:

```bash
cd pedidos360-backend
cp .env.example .env
nano .env
```

Completa al menos:

```dotenv
MYSQL_ROOT_PASSWORD=una-clave-larga
RABBITMQ_USER=pedidos360
RABBITMQ_PASSWORD=otra-clave-larga
ENTRA_TENANT_ID=<tenant-id>
ENTRA_AUDIENCES=<client-id-de-la-api>,api://pedidos360-api
ENTRA_CLIENT_ID=<client-id-del-frontend-spa>
PUBLIC_URL=http://<ip-publica-ec2>
CORS_ALLOWED_ORIGINS=http://<ip-publica-ec2>
FRONTEND_PORT=80
```

No subas `.env` a GitHub.

## 4. Levantar la aplicación

Desde `pedidos360-backend`:

```bash
docker compose -f docker-compose.yml -f docker-compose.aws.yml up -d --build
docker compose -f docker-compose.yml -f docker-compose.aws.yml ps
```

Abre `http://IP_PUBLICA_EC2` en el navegador e inicia sesión con un usuario
de Entra que tenga el App Role correspondiente. Para revisar errores:

```bash
docker compose -f docker-compose.yml -f docker-compose.aws.yml logs --tail=100 bff rabbit-admin frontend
```

RabbitMQ no queda expuesto públicamente. Si necesitas la consola durante la
demostración, usa un túnel SSH:

```bash
ssh -i clave.pem -L 15672:localhost:15672 ubuntu@IP_PUBLICA_EC2
```

Luego abre `http://localhost:15672`.
