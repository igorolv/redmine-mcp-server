# Redmine MCP Server

Локальный MCP-сервер для read-only доступа к корпоративному Redmine.
Позволяет AI-агентам (Claude Code, Cursor, VS Code Copilot и др.) работать с задачами, проектами, участниками, версиями, wiki, вложениями, трудозатратами и справочными данными.

## Быстрый старт

Пошаговые инструкции по настройке «с нуля» для конкретного AI-клиента:

| Клиент | Инструкция | Примечание |
|---|---|---|
| **Qwen Code** | [QWEN_CODE_SETUP.md](QWEN_CODE_SETUP.md) | Работает без VPN. Рекомендуется для начала — проще всего настроить |
| **Claude Code** | [CLAUDE_CODE_SETUP.md](CLAUDE_CODE_SETUP.md) | Требует VPN с tunnel splitting (например [Amnezia](https://amnezia.org)) для одновременного доступа к Claude и корпоративному Redmine |

> Если вы раньше не работали с AI-ассистентами в терминале — начните с **Qwen Code**. Он может использовать бесплатные модели через OpenRouter, не требует VPN, а в случае использования платных моделей оплата из РФ относительно простая.

## Архитектура

Сервер поддерживает только транспорт `stdio`.

### Stdio

```
┌─────────────┐     stdio      ┌──────────────────┐    REST API    ┌──────────┐
│  AI-агент   │ <------------> │  redmine-mcp-    │ -------------> │ Redmine  │
│ (Claude Code│   stdin/stdout │  server (Java)   │   HTTP + API   │ (корп.)  │
│  Cursor...) │                │                  │   Key          │          │
└─────────────┘                └──────────────────┘                └──────────┘
```

AI-клиент запускает сервер как дочерний процесс, обмен по протоколу MCP через stdin/stdout.

## Инструменты

Сервер экспортирует **40 read-only MCP tools**.

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
| `getIssueTree` | Дерево зависимостей: цепочка родителей вверх, подзадачи вниз, связи. Параметры: `issueId`, `depth` (по умолчанию 2, макс 5) |
| `getIssueHistory` | Полная история изменений задачи: таймлайн смен статуса, назначенного, приоритета и других полей, длительность в каждом статусе. Параметры: `issueId` |

### Поиск

| Tool | Описание |
|---|---|
| `searchAll` | Глобальный поиск по всему Redmine: задачи, wiki, новости, коммиты и др. |

### Вложения и Wiki

| Tool | Описание |
|---|---|
| `listAttachments` | Список вложений задачи с размерами и типами |
| `getAttachmentContent` | Содержимое вложений: текстовые (txt, log, xml, json, csv и др.), PDF, Word (.docx), Excel (.xlsx), PowerPoint (.pptx). Для изображений — `getImageAttachment` |
| `getAttachmentTextInfo` | Метаданные извлечённого текста вложения: тип извлечения, общий объём текста и план чанков для больших документов |
| `getAttachmentTextChunk` | Один чанк извлечённого текста вложения для последующего анализа/summary. Параметры: `attachmentId`, `chunkIndex`, `chunkSize` |
| `getImageAttachment` | Скачивание изображений (PNG, JPEG, GIF, BMP, WebP) с автоматическим ресайзом для визуального анализа AI |
| `searchAttachmentContent` | Поиск текста по содержимому вложений задачи или проекта. Извлекает текст из PDF/DOCX/XLSX/PPTX/текстовых файлов, возвращает сниппеты с контекстом. Параметры: `query`, `issueId`, `projectId`, `limit` |
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

### Аналитика

| Tool | Описание |
|---|---|
| `getProjectSummary` | Агрегированная сводка проекта: задачи по статусам, трекерам, приоритетам, назначенным; просроченные; estimated/spent hours. Параметры: `projectId`, `versionId` (опц.) |
| `getUserWorkload` | Анализ нагрузки: открытые задачи по проектам и приоритетам, просроченные, топ задач. Параметры: `userId` (опц., по умолчанию — текущий), `projectId` (опц.) |
| `getVersionChangelog` | Все задачи версии сгруппированные по трекерам, статистика open/closed. Параметры: `projectId`, `versionId` |
| `getBlockerChain` | Рекурсивный обход цепочки блокировок (blocks/blocked_by) вверх и вниз. Параметры: `issueId` |
| `getStaleIssues` | Открытые задачи без обновлений за N дней, отсортированные по давности. Параметры: `projectId`, `daysSinceUpdate` (по умолчанию 30), `limit` |
| `getReleaseRisks` | Оценка рисков релиза: блокеры, просроченные, высокоприоритетные, без назначенного. Параметры: `projectId`, `versionId` |
| `compareVersions` | Сравнение двух версий: уникальные задачи, общие задачи, процент закрытия. Параметры: `projectId`, `versionId1`, `versionId2` |

### Контекст задачи

| Tool | Описание |
|---|---|
| `getIssueFullContext` | Полный контекст задачи одним вызовом: описание, родитель (эпик/стори), siblings (scope фичи), связанные задачи с описаниями, вложения с извлечением текста (PDF/DOCX), последние комментарии. Заменяет 10+ отдельных вызовов. Параметры: `issueId` |
| `getIssueSiblings` | Все задачи с тем же родителем: scope фичи, прогресс, кто что делает. Параметры: `issueId` |
| `findRelatedClosedIssues` | Поиск закрытых задач-референсов: прямые связи, siblings, похожие в проекте. Полезно для изучения «как делали раньше». Параметры: `issueId`, `limit` (опц.) |
| `findLatestAttachment` | Поиск последней версии документа по паттерну имени. Ищет в задаче, родителе, siblings, связанных. Параметры: `pattern`, `issueId`, `searchProject` (опц.) |
| `getIssueNetwork` | Полная сеть связей задачи: все типы (relates, blocks, precedes, duplicates, parent/child). BFS-обход на заданную глубину. Параметры: `issueId`, `depth` (опц., по умолчанию 2, макс 3) |

Все инструменты **read-only** — данные в Redmine не изменяются.

## Стек

- Java 25, Spring Boot 4.0, Spring AI MCP (stdio transport)
- Apache PDFBox 3.0.5 — извлечение текста из PDF
- Apache POI 5.4 — извлечение текста из Word, Excel, PowerPoint
- Gradle 9.3.1 с version catalog

## Сборка

```bash
# Указать JDK 25+, если не является JDK по умолчанию:
export JAVA_HOME="$HOME/.jdks/jdk-25.0.2"

./gradlew build
```

Результат: `build/libs/redmine-mcp-server.jar`

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

```bash
REDMINE_URL=https://redmine.example.com REDMINE_API_KEY=your_key \
  java -jar build/libs/redmine-mcp-server.jar
```

## Подключение к AI-клиенту

Добавить в конфигурацию клиента:

```json
{
  "command": "java",
  "args": ["-jar", "<абсолютный-путь>/redmine-mcp-server.jar"],
  "env": {
    "REDMINE_URL": "https://redmine.example.com",
    "REDMINE_API_KEY": "your_api_key"
  }
}
```

Куда именно:

| Клиент | Способ подключения |
|---|---|
| Claude Code | `claude mcp add --scope user -e REDMINE_URL=... -e REDMINE_API_KEY=... -- redmine java -jar /path/to/redmine-mcp-server.jar` |
| Qwen Code | `~/.qwen/settings.json` -> `"mcpServers"` -> `"redmine"` |
| VS Code | `.vscode/mcp.json` -> `"servers"` -> `"redmine"` |
| Cursor | `.cursor/mcp.json` -> `"mcpServers"` -> `"redmine"` |
| Claude Desktop | `claude_desktop_config.json` -> `"mcpServers"` -> `"redmine"` |

Для Claude Code без `--scope user` сервер добавится только для текущего проекта. Проверить подключение: `claude mcp list`.

После добавления перезапустить клиент.

## Структура проекта

```
├── src/main/java/ru/it_spectrum/ai/redmine/mcp/
│   ├── RedmineMcpServerApplication.java   — точка входа Spring Boot
│   ├── config/
│   │   ├── RedmineProperties.java         — url + apiKey из env
│   │   └── RedmineConfig.java             — RestClient с автодобавлением http://
│   ├── client/
│   │   ├── RedmineClient.java             — обёртка над Redmine REST API
│   │   ├── AttachmentTextCache.java       — кэш извлечённого текста вложений
│   │   └── DocumentTextExtractor.java     — извлечение текста из PDF/DOCX/XLSX/PPTX
│   ├── model/
│   │   ├── IdName.java                    — пара id/name (проект, статус, и т.д.)
│   │   ├── AttachmentTextChunk.java       — чанк извлечённого текста вложения
│   │   ├── AttachmentTextInfo.java        — метаданные и план чанков для текста вложения
│   │   ├── RedmineAttachment.java         — вложение
│   │   ├── RedmineIssue.java              — задача + Journal + Relation
│   │   ├── RedmineMembership.java         — участник проекта
│   │   ├── RedmineProject.java            — проект
│   │   ├── RedmineQuery.java              — сохранённый запрос (фильтр)
│   │   ├── RedmineSearchResult.java       — результат поиска
│   │   ├── RedmineTimeEntry.java          — запись трудозатрат
│   │   ├── RedmineUser.java               — пользователь
│   │   ├── RedmineVersion.java            — версия/майлстоун
│   │   └── RedmineWikiPage.java           — wiki-страница
│   └── tools/
│       ├── AttachmentTools.java           — 5 MCP-инструментов для вложений и изображений
│       ├── ContextTools.java              — 5 MCP-инструментов для контекста задачи
│       ├── IssueTools.java                — 5 MCP-инструментов для задач и поиска
│       ├── ProjectTools.java              — 4 MCP-инструмента для проектов
│       ├── ReferenceDataTools.java        — 6 MCP-инструментов для справочников
│       ├── TimeEntryTools.java            — 2 MCP-инструмента для трудозатрат
│       ├── UserTools.java                 — 1 MCP-инструмент для текущего пользователя
│       └── WikiTools.java                 — 2 MCP-инструмента для wiki
└── src/main/resources/
    ├── application.yml                    — конфигурация MCP-сервера (stdio)
    └── logback-spring.xml                 — конфигурация логирования
```

## Troubleshooting

- **"Gradle requires JVM 17 or later"** — установить `JAVA_HOME` на JDK 25+
- **Connection refused / 401** — проверить `REDMINE_URL` и `REDMINE_API_KEY`. Тест: `curl -H "X-Redmine-API-Key: <key>" <url>/users/current.json`
- **Нет результатов поиска** — убедиться, что `/search.json` доступен в Redmine (может быть отключён администратором)
