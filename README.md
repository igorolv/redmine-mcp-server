# Redmine MCP Server

Локальный MCP-сервер для read-only доступа к корпоративному Redmine.
Позволяет AI-агентам (Claude Code, Cursor, VS Code Copilot и др.) работать с задачами, проектами, участниками, версиями, wiki и вложениями.

## Архитектура

```
┌─────────────┐     stdio      ┌──────────────────┐    REST API    ┌──────────┐
│  AI-агент   │ <------------> │  redmine-mcp-    │ -------------> │ Redmine  │
│ (Claude Code│   stdin/stdout │  server (Java)   │   HTTP + API   │ (корп.)  │
│  Cursor...) │                │                  │   Key          │          │
└─────────────┘                └──────────────────┘                └──────────┘
```

1. AI-клиент при старте запускает `java -jar redmine-mcp-server.jar` как дочерний процесс
2. Клиент и сервер обмениваются сообщениями по протоколу MCP через stdin/stdout
3. Сервер сообщает клиенту список доступных инструментов с описаниями и параметрами
4. При запросе пользователя AI-агент вызывает нужный инструмент, сервер выполняет запрос к Redmine REST API и возвращает результат

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
| `listIssues` | Список задач с фильтрами: проект, статус, трекер, назначенный, приоритет, версия, сортировка |
| `searchIssues` | Полнотекстовый поиск задач с детальными результатами |
| `getIssue` | Детали задачи: описание, статус, назначенный, даты, примечания, связи, кастомные поля, вложения |

### Поиск

| Tool | Описание |
|---|---|
| `searchAll` | Глобальный поиск по всему Redmine: задачи, wiki, новости, коммиты и др. |

### Вложения и Wiki

| Tool | Описание |
|---|---|
| `listAttachments` | Список вложений задачи с размерами и типами |
| `getAttachmentContent` | Содержимое вложений: текстовые (txt, log, xml, json, csv и др.), PDF, Word (.docx), Excel (.xlsx), PowerPoint (.pptx). Для изображений и прочих бинарных — только метаданные |
| `getWikiPage` | Содержимое wiki-страницы проекта |
| `listWikiPages` | Список всех wiki-страниц проекта |

### Трудозатраты

| Tool | Описание |
|---|---|
| `listTimeEntries` | Залогированное время с фильтрами: проект, задача, пользователь, период |

### Справочники

| Tool | Описание |
|---|---|
| `listStatuses` | Все статусы задач (ID + название) — для фильтрации в `listIssues` |
| `listTrackers` | Все трекеры (ID + название) — для фильтрации в `listIssues` |
| `listPriorities` | Все приоритеты (ID + название) — для фильтрации в `listIssues` |

Все инструменты **read-only** — данные в Redmine не изменяются.

## Стек

- Java 25, Spring Boot 4.0, Spring AI MCP (stdio transport)
- Apache PDFBox 3.0 — извлечение текста из PDF
- Apache POI 5.4 — извлечение текста из Word, Excel, PowerPoint
- Gradle 9.3 с version catalog

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

## Подключение к AI-клиенту

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

Куда именно:

| Клиент | Файл конфигурации | Ключ |
|---|---|---|
| Claude Code | `~/.claude/settings.json` -> `"mcpServers"` | `"redmine"` |
| Qwen Code | `~/.qwen/settings.json` -> `"mcpServers"` | `"redmine"` |
| VS Code | `.vscode/mcp.json` -> `"servers"` | `"redmine"` |
| Cursor | `.cursor/mcp.json` -> `"mcpServers"` | `"redmine"` |
| Claude Desktop | `claude_desktop_config.json` -> `"mcpServers"` | `"redmine"` |

После добавления перезапустить клиент.

## Структура проекта

```
src/main/java/ru/it_spectrum/ai/redmine/mcp/
├── RedmineMcpServerApplication.java   — точка входа Spring Boot
├── config/
│   ├── RedmineProperties.java         — url + apiKey из env
│   └── RedmineConfig.java             — RestClient с автодобавлением http://
├── client/
│   └── RedmineClient.java             — обёртка над Redmine REST API
├── model/
│   ├── IdName.java                    — пара id/name (проект, статус, и т.д.)
│   ├── RedmineIssue.java             — задача + Journal + Relation
│   ├── RedmineProject.java           — проект
│   ├── RedmineMembership.java        — участник проекта
│   ├── RedmineVersion.java           — версия/майлстоун
│   ├── RedmineWikiPage.java          — wiki-страница
│   ├── RedmineAttachment.java        — вложение
│   ├── RedmineTimeEntry.java         — запись трудозатрат
│   ├── RedmineUser.java              — пользователь
│   └── RedmineSearchResult.java      — результат поиска
└── tools/
    └── RedmineTools.java              — 17 MCP-инструментов (read-only)
```

## Troubleshooting

- **"Gradle requires JVM 17 or later"** — установить `JAVA_HOME` на JDK 25+
- **Connection refused / 401** — проверить `REDMINE_URL` и `REDMINE_API_KEY`. Тест: `curl -H "X-Redmine-API-Key: <key>" <url>/users/current.json`
- **Нет результатов поиска** — убедиться, что `/search.json` доступен в Redmine (может быть отключён администратором)
