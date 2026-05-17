# Redmine MCP Server

Локальный MCP-сервер для read-only доступа к корпоративному Redmine.
Позволяет AI-агентам (Claude Code, Cursor, VS Code Copilot и др.) работать с задачами, проектами, участниками, версиями, wiki, вложениями, трудозатратами и справочными данными.

## Быстрый старт

Эта документация описывает установку и подключение именно Redmine MCP Server. Установка и настройка самих AI-клиентов здесь не рассматриваются.

1. Установите JDK 25+.
2. Соберите сервер: `./gradlew build`.
3. Получите `REDMINE_URL` и `REDMINE_API_KEY`.
4. Добавьте собранный JAR в MCP-конфигурацию вашего клиента.

Клиентские примеры подключения:

| Клиент | Инструкция |
|---|---|
| Claude Code | [CLAUDE_CODE_SETUP.md](CLAUDE_CODE_SETUP.md) |
| Qwen Code | [QWEN_CODE_SETUP.md](QWEN_CODE_SETUP.md) |

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

Сервер экспортирует **31 read-only MCP tools**.

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
| `listIssues` | Список задач с фильтрами: проект, статус, трекер, назначенный, приоритет, версия, сохранённый запрос, кастомные поля (`customFieldFilters` в формате `cf_<id>=value`), сортировка |
| `searchIssues` | Полнотекстовый поиск задач с детальными результатами |
| `getIssue` | Детали задачи: описание, статус, назначенный, даты, примечания, связи, кастомные поля, вложения, связанные редакции (`changesets`) |
| `getMyIssues` | Задачи текущего пользователя. Параметры: `projectId`, `statusId`, `sort`, `limit`, `offset` |
| `getIssueTree` | Дерево зависимостей: цепочка родителей вверх, подзадачи вниз, связи. Параметры: `issueId`, `depth` (по умолчанию 2, макс 5) |

### Поиск

| Tool | Описание |
|---|---|
| `searchAll` | Глобальный поиск по Redmine: задачи, wiki, новости, документы, коммиты и др. Параметры: `query`, `projectId`, `types`, `limit`, `offset` |

### Вложения и Wiki

| Tool | Описание |
|---|---|
| `getAttachment` | Скачивает оригинальный файл вложения в локальный snapshot-каталог, возвращает `localPath`/`fileUri` и сразу добавляет текстовый контекст в `parts[]`, если формат поддержан: txt/log/xml/json/csv, PDF, Word (.docx), Excel (.xlsx), PowerPoint (.pptx), ZIP. ZIP может дать отдельную часть на каждый entry |
| `getWikiPage` | Содержимое wiki-страницы проекта |
| `listWikiPages` | Список всех wiki-страниц проекта |
| `searchWikiPages` | Полнотекстовый поиск по wiki-страницам. Параметры: `query`, `projectId`, `limit`, `offset` |

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
| `listTimeEntryActivities` | Типы активностей для интерпретации существующих трудозатрат (ID + название) |

### Аналитика

| Tool | Описание |
|---|---|
| `getProjectSummary` | Агрегированная сводка проекта: общий счётчик open/closed; разрезы проанализированных открытых задач по статусам, трекерам, приоритетам, назначенным; просроченные; estimated/spent hours. Анализирует до 500 открытых задач и возвращает флаг усечения. Параметры: `projectId`, `versionId` (опц.) |
| `getUserWorkload` | Анализ нагрузки: открытые задачи по проектам и приоритетам, просроченные, топ задач. Анализирует до 500 открытых задач и возвращает флаг усечения. Параметры: `userId` (опц., по умолчанию — текущий), `projectId` (опц.) |
| `getVersionChangelog` | Задачи версии, сгруппированные по трекерам, статистика open/closed. Анализирует до 500 задач и возвращает флаг усечения. Параметры: `projectId`, `versionId` |
| `getBlockerChain` | Рекурсивный обход цепочки блокировок (blocks/blocked_by) вверх и вниз, ограничен глубиной 10 и 30 загруженными задачами. Параметры: `issueId` |
| `getStaleIssues` | Открытые задачи без обновлений за N дней, отсортированные по давности. Параметры: `projectId`, `daysSinceUpdate` (по умолчанию 30), `limit` |
| `getReleaseRisks` | Оценка рисков релиза: блокеры, просроченные, высокоприоритетные, без назначенного. Анализирует до 500 открытых задач и возвращает флаг усечения. Параметры: `projectId`, `versionId` |
| `compareVersions` | Сравнение двух версий: уникальные задачи, общие задачи, процент закрытия. Анализирует до 500 задач на версию и возвращает флаг усечения. Параметры: `projectId`, `versionId1`, `versionId2` |

### Контекст задачи

| Tool | Описание |
|---|---|
| `getIssueFullContext` | Полный контекст задачи одним вызовом: описание, интерпретированная история с длительностью статусов, единый список окружающих задач с ролями (`parent`, `sibling`, `child`, `related`), вложения задачи и parent-задачи в формате `getAttachment` с inline-бюджетами для текста и ссылками `localPath`/`fileUri` для изображений, последние комментарии и флаги усечения. Параметры: `issueId` |

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

Серверу нужны `REDMINE_URL` и `REDMINE_API_KEY`; остальные переменные опциональны:

| Переменная | Описание |
|---|---|
| `REDMINE_URL` | Базовый URL Redmine (например `https://redmine.example.com`) |
| `REDMINE_API_KEY` | API-ключ пользователя Redmine |
| `REDMINE_MCP_DATA_DIR` | Каталог локальных данных сервера; по умолчанию `~/.redmine-mcp-server` |
| `REDMINE_MCP_ATTACHMENT_PREVIEW_LIMIT` | Лимит текста на `part` для `getAttachment`; по умолчанию `100000` символов |
| `REDMINE_MCP_FULL_CONTEXT_ATTACHMENT_TEXT_LIMIT` | Лимит inline-текста на одно вложение в `getIssueFullContext`; по умолчанию `10000` символов |
| `REDMINE_MCP_FULL_CONTEXT_TOTAL_ATTACHMENT_TEXT_LIMIT` | Суммарный лимит inline-текста вложений в `getIssueFullContext`; по умолчанию `30000` символов |

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

Логи пишутся в `${REDMINE_MCP_DATA_DIR:-~/.redmine-mcp-server}/logs/redmine-mcp-server.log`.
Файл ротируется по дате и размеру: `10MB`, хранение `30` дней, общий лимит `512MB`.

При загрузке issue сервер сохраняет снимок на диск в
`${REDMINE_MCP_DATA_DIR:-~/.redmine-mcp-server}/issues/<issue-id>/`: `issue.json`,
`snapshot.json` с метаданными снимка, `attachments.json` и каталог `extracted/<attachment-id>/`
для производных файлов. Вложения материализуются в `attachments/` с именами вида
`<attachment-id>__<filename>` и могут переиспользоваться между снимками, если локальный файл
уже существует и его размер совпадает с metadata Redmine.

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

После добавления перезапустить клиент.

## Эксплуатация и безопасность

Этот MCP-сервер предназначен для локального запуска рядом с AI-клиентом. Он не открывает HTTP-порт и не принимает входящие сетевые подключения: клиент запускает JAR как дочерний процесс и общается с ним через `stdin/stdout`.

### Модель доступа

- Сервер использует права того пользователя Redmine, чей API-ключ указан в `REDMINE_API_KEY`.
- Все MCP-инструменты read-only: сервер не создаёт, не изменяет и не удаляет задачи, комментарии, wiki-страницы, вложения или трудозатраты.
- Доступные проекты, задачи и вложения определяются правами пользователя в Redmine. Если пользователь не видит объект в Redmine, сервер тоже не должен получить к нему доступ.
- API-ключ нужно хранить как секрет. Не коммитьте его в репозиторий, shell-скрипты, `.vscode/mcp.json`, `.cursor/mcp.json` или другие общие файлы проекта.

### Какие данные передаются AI-клиенту

AI-клиент получает ровно те данные, которые запрашивает через MCP-инструменты:

- карточки задач: тема, описание, статус, приоритет, назначенный, автор, даты, связи, подзадачи, комментарии, кастомные поля;
- сведения о проектах, версиях, участниках, справочниках и трудозатратах;
- wiki-страницы;
- метаданные вложений;
- локальные пути к оригинальным файлам вложений и текст, извлечённый из PDF, DOCX, XLSX, PPTX, ZIP и текстовых файлов через `getAttachment`.

Перед подключением к внешнему или облачному AI-клиенту проверьте внутренние правила компании: данные Redmine могут содержать коммерческую тайну, персональные данные, логи, ключи, дампы ошибок и содержимое документов.

### Лимиты обработки

В коде есть защитные лимиты, чтобы один большой документ или связанная сеть задач не перегружали MCP-клиент:

| Область | Лимит |
|---|---|
| Каждая текстовая часть `getAttachment.parts[]` | до 50 000 символов, дальше текст обрезается |
| ZIP-глубина | 1 уровень |
| ZIP-файлы | до 100 записей |
| ZIP-файл внутри архива | до 10 MB |
| ZIP-архив суммарно | до 50 MB извлечённых данных |
| `getIssueTree` | глубина до 5, максимум 50 задач |

Часть обычных list инструментов (`listIssues`, `listProjects`, `listTimeEntries`, `listQueries`) принимает `limit` и `offset` напрямую. Для устойчивой работы лучше не запрашивать чрезмерно большие страницы; практичный диапазон — 25-100 элементов за вызов.

### Диагностика

Проверка окружения:

```bash
java -version
echo "$REDMINE_URL"
test -n "$REDMINE_API_KEY" && echo "REDMINE_API_KEY is set"
```

Проверка доступа к Redmine REST API:

```bash
curl -H "X-Redmine-API-Key: <key>" <url>/users/current.json
```

Проверка сборки:

```bash
./gradlew test
./gradlew build
```

Проверка интеграционных тестов с живым Redmine:

```bash
REDMINE_URL=<url> REDMINE_API_KEY=<key> ./gradlew integrationTest
```

Интеграционные тесты требуют доступный Redmine и реальные тестовые данные. Unit-тесты по умолчанию исключают тесты с тегом `integration`.

### Известные эксплуатационные ограничения

- HTTP-таймауты и retry-политика сейчас не настраиваются отдельно. Если Redmine долго не отвечает, MCP-вызов может ждать ответа дольше, чем удобно для AI-клиента.
- Ошибки Redmine (`401`, `403`, `404`, `5xx`) сейчас в основном обрабатываются на уровне Spring `RestClient`; для пользователя AI-клиента сообщение может быть менее дружелюбным, чем специализированная ошибка MCP tool.
- Поиск зависит от настроек Redmine. Если `/search.json` отключён администратором, `searchAll`, `searchIssues` и `searchWikiPages` могут не возвращать ожидаемые результаты.
- Извлечение текста из PDF работает только для PDF с текстовым слоем. Сканированные документы без OCR будут определены как PDF без извлекаемого текста.
- Изображения не перекодируются. `getAttachment` возвращает путь к оригинальному файлу; текстовые `parts[]` для изображений остаются пустыми.

## Структура проекта

```
├── src/main/java/ru/it_spectrum/ai/redmine/mcp/
│   ├── RedmineMcpServerApplication.java   — точка входа Spring Boot
│   ├── config/
│   │   ├── RedmineClientProperties.java   — url + apiKey из env
│   │   └── RedmineConfig.java             — RestClient с автодобавлением http://
│   ├── client/
│   │   ├── RedmineClient.java             — обёртка над Redmine REST API
│   │   └── DocumentTextExtractor.java     — извлечение текста из PDF/DOCX/XLSX/PPTX/ZIP
│   ├── model/
│   │   ├── IdName.java                    — пара id/name (проект, статус, и т.д.)
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
│       ├── AnalysisTools.java             — 7 MCP-инструментов аналитики и анализа рисков
│       ├── AttachmentTools.java           — 1 MCP-инструмент для файлов и контекста вложений
│       ├── ContextTools.java              — 1 MCP-инструмент для контекста задачи
│       ├── IssueTools.java                — 6 MCP-инструментов для задач
│       ├── ProjectTools.java              — 4 MCP-инструмента для проектов
│       ├── ReferenceDataTools.java        — 6 MCP-инструментов для справочников
│       ├── SearchTools.java               — 1 MCP-инструмент для глобального поиска
│       ├── TimeEntryTools.java            — 2 MCP-инструмента для трудозатрат
│       ├── UserTools.java                 — 1 MCP-инструмент для текущего пользователя
│       └── WikiTools.java                 — 3 MCP-инструмента для wiki
└── src/main/resources/
    ├── application.yml                    — конфигурация MCP-сервера (stdio)
    └── logback-spring.xml                 — конфигурация логирования
```

## Troubleshooting

- **"Gradle requires JVM 17 or later"** — установить `JAVA_HOME` на JDK 25+
- **Connection refused / 401** — проверить `REDMINE_URL` и `REDMINE_API_KEY`. Тест: `curl -H "X-Redmine-API-Key: <key>" <url>/users/current.json`
- **Нет результатов поиска** — убедиться, что `/search.json` доступен в Redmine (может быть отключён администратором)
