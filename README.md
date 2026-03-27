# Redmine MCP Server

Локальный MCP-сервер для read-only доступа к корпоративному Redmine.
Позволяет AI-агентам (Claude Code, Cursor, VS Code Copilot и др.) работать с задачами, проектами, участниками, версиями, wiki и вложениями.

## Быстрый старт

Пошаговые инструкции по настройке «с нуля» для конкретного AI-клиента:

| Клиент | Инструкция | Примечание |
|---|---|---|
| **Qwen Code** | [QWEN_CODE_SETUP.md](QWEN_CODE_SETUP.md) | Работает без VPN. Рекомендуется для начала — проще всего настроить |
| **Claude Code** | [CLAUDE_CODE_SETUP.md](CLAUDE_CODE_SETUP.md) | Требует VPN с tunnel splitting (например [Amnezia](https://amnezia.org)) для одновременного доступа к Claude и корпоративному Redmine |

> Если вы раньше не работали с AI-ассистентами в терминале — начните с **Qwen Code**. Он может использовать бесплатные модели через OpenRouter, не требует VPN, а в случае использования платных моделей оплата из РФ относительно простая.

## Архитектура

Сервер поддерживает два режима транспорта:

### Stdio (по умолчанию)

```
┌─────────────┐     stdio      ┌──────────────────┐    REST API    ┌──────────┐
│  AI-агент   │ <------------> │  redmine-mcp-    │ -------------> │ Redmine  │
│ (Claude Code│   stdin/stdout │  server (Java)   │   HTTP + API   │ (корп.)  │
│  Cursor...) │                │                  │   Key          │          │
└─────────────┘                └──────────────────┘                └──────────┘
```

AI-клиент запускает сервер как дочерний процесс, обмен по протоколу MCP через stdin/stdout.

### SSE (Server-Sent Events)

```
┌─────────────┐     HTTP/SSE    ┌──────────────────┐    REST API    ┌──────────┐
│  AI-агент   │ <-------------> │  redmine-mcp-    │ -------------> │ Redmine  │
│ (Claude Code│   порт 8080    │  server (Java)   │   HTTP + API   │ (корп.)  │
│  Cursor...) │                │  Docker / host   │   Key          │          │
└─────────────┘                └──────────────────┘                └──────────┘
```

Сервер работает как HTTP-сервис (можно запустить в Docker). AI-клиент подключается по URL.

## Инструменты

### Пользователь

| Tool | Описание |
|---|---|
| `getCurrentUser` | Текущий пользователь: ID, логин, группы, проекты. Полезен для фильтрации «мои задачи» |

### Проекты

| Tool | Описание |
|---|---|
| `listProjects` | Список всех доступных проектов |
| `getProject` | Детали проекта: трекеры, модули, описание |
| `listProjectMembers` | Участники проекта с ролями |
| `listVersions` | Версии (майлстоуны) проекта |

### Задачи

| Tool | Описание |
|---|---|
| `listIssues` | Список задач с фильтрами: проект, статус, трекер, назначенный, приоритет, версия, сохранённый запрос, сортировка |
| `searchIssues` | Полнотекстовый поиск задач с детальными результатами |
| `getIssue` | Детали задачи: описание, статус, назначенный, даты, примечания, связи, кастомные поля, вложения |
| `getMyIssues` | Задачи текущего пользователя. Параметры: `projectId`, `statusId`, `sort`, `limit`, `offset` |

### Поиск

| Tool | Описание |
|---|---|
| `searchAll` | Глобальный поиск по всему Redmine: задачи, wiki, новости, коммиты и др. |

### Вложения и Wiki

| Tool | Описание |
|---|---|
| `listAttachments` | Список вложений задачи с размерами и типами |
| `getAttachmentContent` | Содержимое вложений: текстовые (txt, log, xml, json, csv и др.), PDF, Word (.docx), Excel (.xlsx), PowerPoint (.pptx). Для изображений — `getImageAttachment` |
| `getImageAttachment` | Скачивание изображений (PNG, JPEG, GIF, BMP, WebP) с автоматическим ресайзом для визуального анализа AI |
| `getWikiPage` | Содержимое wiki-страницы проекта |
| `listWikiPages` | Список всех wiki-страниц проекта |

### Трудозатраты

| Tool | Описание |
|---|---|
| `listTimeEntries` | Залогированное время с фильтрами: проект, задача, пользователь, период |
| `getMyTimeEntries` | Залогированное время текущего пользователя. Параметры: `projectId`, `issueId`, `from`, `to`, `limit`, `offset` |

### Справочники

| Tool | Описание |
|---|---|
| `listQueries` | Сохранённые запросы (пользовательские фильтры) — ID + название. Используйте ID с `listIssues(queryId)` для применения фильтра, в т.ч. по кастомным полям |
| `listStatuses` | Все статусы задач (ID + название) — для фильтрации в `listIssues` |
| `listTrackers` | Все трекеры (ID + название) — для фильтрации в `listIssues` |
| `listPriorities` | Все приоритеты (ID + название) — для фильтрации в `listIssues` |
| `listIssueCategories` | Категории задач проекта (ID + название) |
| `listTimeEntryActivities` | Типы активностей для трудозатрат (ID + название) |

Все инструменты **read-only** — данные в Redmine не изменяются.

## Стек

- Java 25, Spring Boot 4.0, Spring AI MCP (stdio + SSE transport)
- Apache PDFBox 3.0.5 — извлечение текста из PDF
- Apache POI 5.4 — извлечение текста из Word, Excel, PowerPoint
- Gradle 9.3.1 с version catalog

## Сборка

```bash
# Указать JDK 25+, если не является JDK по умолчанию:
export JAVA_HOME="$HOME/.jdks/jdk-25.0.2"

./gradlew build
```

Результат: `build/libs/redmine-mcp-server-0.1.0-SNAPSHOT.jar`

## Настройка

Серверу нужны две переменные окружения:

| Переменная | Описание |
|---|---|
| `REDMINE_URL` | Базовый URL Redmine (например `https://redmine.example.com`) |
| `REDMINE_API_KEY` | API-ключ пользователя Redmine |

### Как получить `REDMINE_URL`

Откройте Redmine в браузере и скопируйте адрес из адресной строки **без** пути — только схему и домен.

Примеры:

| В адресной строке браузера | Значение `REDMINE_URL` |
|---|---|
| `https://redmine.example.com/projects/myproject` | `https://redmine.example.com` |
| `http://192.168.1.50:3000/issues/123` | `http://192.168.1.50:3000` |
| `http://10.0.0.5/redmine/projects` | `http://10.0.0.5/redmine` |

> Если Redmine доступен только по IP-адресу (без доменного имени), используйте IP как есть, включая порт, если он отличается от стандартного (80/443). Если Redmine развёрнут по подпути (например `/redmine`), его тоже нужно включить в URL.

### Как получить `REDMINE_API_KEY`

1. Войдите в Redmine под своей учётной записью
2. Нажмите **«Моя учётная запись»** (правый верхний угол)
3. В правой колонке найдите блок **«Ключ доступа к API»**
4. Нажмите **«Показать»** — отобразится ваш персональный API-ключ
5. Скопируйте ключ и используйте его как значение `REDMINE_API_KEY`

> Если блок «Ключ доступа к API» не отображается, обратитесь к администратору Redmine — возможно, REST API отключён в настройках.

## Запуск

### Stdio (по умолчанию)

```bash
REDMINE_URL=https://redmine.example.com REDMINE_API_KEY=your_key \
  java -jar build/libs/redmine-mcp-server-0.1.0-SNAPSHOT.jar
```

### SSE

```bash
REDMINE_URL=https://redmine.example.com REDMINE_API_KEY=your_key \
  java -jar build/libs/redmine-mcp-server-0.1.0-SNAPSHOT.jar --spring.profiles.active=sse
```

Сервер поднимется на порту 8080 (настраивается через `SERVER_PORT`).

### Docker

```bash
cd docker
REDMINE_URL=https://redmine.example.com REDMINE_API_KEY=your_key docker compose up
```

В Docker сервер всегда работает в SSE-режиме. Порт настраивается через `SERVER_PORT` (по умолчанию 8080).

## Подключение к AI-клиенту

### Stdio-режим

Добавить в конфигурацию клиента:

```json
{
  "command": "java",
  "args": ["-jar", "<абсолютный-путь>/redmine-mcp-server-0.1.0-SNAPSHOT.jar"],
  "env": {
    "REDMINE_URL": "https://redmine.example.com",
    "REDMINE_API_KEY": "your_api_key"
  }
}
```

### SSE-режим (в т.ч. Docker)

```json
{
  "url": "http://localhost:8080/sse"
}
```

Куда именно:

| Клиент | Способ подключения |
|---|---|
| Claude Code | `claude mcp add --scope user -e REDMINE_URL=... -e REDMINE_API_KEY=... -- redmine java -jar /path/to/redmine-mcp-server-0.1.0-SNAPSHOT.jar` |
| Qwen Code | `~/.qwen/settings.json` -> `"mcpServers"` -> `"redmine"` |
| VS Code | `.vscode/mcp.json` -> `"servers"` -> `"redmine"` |
| Cursor | `.cursor/mcp.json` -> `"mcpServers"` -> `"redmine"` |
| Claude Desktop | `claude_desktop_config.json` -> `"mcpServers"` -> `"redmine"` |

Для Claude Code без `--scope user` сервер добавится только для текущего проекта. Проверить подключение: `claude mcp list`.

После добавления перезапустить клиент.

## Структура проекта

```
├── docker/
│   ├── Dockerfile                     — мультиэтапная сборка образа
│   ├── docker-compose.yml             — запуск в SSE-режиме
│   └── .dockerignore
├── src/main/java/ru/it_spectrum/ai/redmine/mcp/
│   ├── RedmineMcpServerApplication.java   — точка входа Spring Boot
│   ├── config/
│   │   ├── RedmineProperties.java         — url + apiKey из env
│   │   └── RedmineConfig.java             — RestClient с автодобавлением http://
│   ├── client/
│   │   └── RedmineClient.java             — обёртка над Redmine REST API
│   ├── model/
│   │   ├── IdName.java                    — пара id/name (проект, статус, и т.д.)
│   │   ├── RedmineIssue.java             — задача + Journal + Relation
│   │   ├── RedmineProject.java           — проект
│   │   ├── RedmineMembership.java        — участник проекта
│   │   ├── RedmineVersion.java           — версия/майлстоун
│   │   ├── RedmineWikiPage.java          — wiki-страница
│   │   ├── RedmineAttachment.java        — вложение
│   │   ├── RedmineTimeEntry.java         — запись трудозатрат
│   │   ├── RedmineUser.java              — пользователь
│   │   ├── RedmineQuery.java             — сохранённый запрос (фильтр)
│   │   └── RedmineSearchResult.java      — результат поиска
│   └── tools/
│       └── RedmineTools.java              — 23 MCP-инструмента (read-only)
└── src/main/resources/
    ├── application.yml                    — основная конфигурация (stdio)
    ├── application-sse.yml                — профиль SSE (HTTP-сервер)
    └── logback-spring.xml               — конфигурация логирования
```

## Troubleshooting

- **"Gradle requires JVM 17 or later"** — установить `JAVA_HOME` на JDK 25+
- **Connection refused / 401** — проверить `REDMINE_URL` и `REDMINE_API_KEY`. Тест: `curl -H "X-Redmine-API-Key: <key>" <url>/users/current.json`
- **Нет результатов поиска** — убедиться, что `/search.json` доступен в Redmine (может быть отключён администратором)
