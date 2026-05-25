# Redmine MCP Server

Локальный MCP-сервер для read-only доступа к корпоративному Redmine.
Позволяет AI-агентам (Claude Code, Cursor, VS Code Copilot и др.) работать с задачами, проектами, участниками, версиями, wiki, вложениями, трудозатратами и справочными данными.

## Быстрый старт

Эта документация описывает установку и подключение именно Redmine MCP Server. Установка и настройка самих AI-клиентов здесь не рассматриваются.

1. Установите JDK 25+.
2. Соберите сервер: `./gradlew build`.
3. Получите `REDMINE_URL` и `REDMINE_API_KEY`.
4. Проверьте, что JAR запускается (см. [Проверка запуска](#проверка-запуска)).
5. Добавьте собранный JAR в MCP-конфигурацию вашего клиента (см. [Подключение к AI-клиенту](#подключение-к-ai-клиенту)).

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
| `getIssue` | Детали задачи: описание, статус, назначенный, даты, примечания, связи, кастомные поля, вложения, связанные редакции (`changesets`). Параметры: `issueId`, `responseProfile` (`default`, `review`, `full`; опц.) |
| `getMyIssues` | Задачи текущего пользователя. Параметры: `projectId`, `statusId`, `sort`, `limit`, `offset` |
| `getIssueTree` | Дерево зависимостей: цепочка родителей вверх, подзадачи вниз, связи. Параметры: `issueId`, `depth` (по умолчанию 2, макс 5) |

### Поиск

| Tool | Описание |
|---|---|
| `searchAll` | Глобальный поиск по Redmine: задачи, wiki, новости, документы, коммиты и др. Параметры: `query`, `projectId`, `types`, `limit`, `offset` |

### Вложения и Wiki

| Tool | Описание |
|---|---|
| `getAttachment` | Скачивает оригинальный файл вложения в локальный snapshot-каталог, возвращает `localPath`/`fileUri` и сразу добавляет текстовый контекст в `parts[]`, если формат поддержан: txt/log/xml/json/csv, PDF, Word (.docx), Excel (.xlsx), PowerPoint (.pptx), ZIP. ZIP может дать отдельную часть на каждый entry. Параметры: `issueId`, `attachmentId`, `maxChars`, `partLimit`, `responseProfile` (`default`, `review`, `full`; опц.) |
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

Все инструменты **read-only** — данные в Redmine не изменяются.

## MCP-промпты

Сервер также экспортирует **MCP prompt** для типового сценария расследования инцидентов:

| Prompt | Описание |
|---|---|
| `incident-brief` | Быстрый обзор инцидента: получает задачу через `getIssue`, скачивает все вложения через `getAttachment` с коротким preview и формирует краткий Markdown-отчёт |

## Стек

- Java 25, Spring Boot 4.0.0, Spring AI MCP 2.0.0-M6 (stdio transport)
- Apache PDFBox 3.0.5 — извлечение текста из PDF
- Apache POI 5.4.0 — извлечение текста из Word, Excel, PowerPoint
- Apache Tika 3.2.0 (core + parsers-standard) — fallback-парсер и извлечение метаданных
- Pandoc (опционально, внешний бинарь) — улучшенное преобразование DOCX в текст/markdown, когда доступен в `PATH`; при отсутствии сервер использует POI
- Gradle 9.3.1 с version catalog (`gradle/libs.versions.toml`)

## Сборка

Linux/macOS:

```bash
# Указать JDK 25+, если не является JDK по умолчанию:
export JAVA_HOME="$HOME/.jdks/jdk-25.0.2"

./gradlew build
```

Windows PowerShell:

```powershell
# Указать JDK 25+, если не является JDK по умолчанию:
$env:JAVA_HOME="C:\Program Files\Java\jdk-25"

.\gradlew.bat build
```

Результат: `build/libs/redmine-mcp-server.jar`

## Настройка

Серверу нужны `REDMINE_URL` и `REDMINE_API_KEY`; остальные переменные опциональны:

| Переменная | Описание |
|---|---|
| `REDMINE_URL` | Базовый URL Redmine (например `https://redmine.example.com`) |
| `REDMINE_API_KEY` | API-ключ пользователя Redmine |
| `REDMINE_MCP_DATA_DIR` | Каталог локальных данных сервера; по умолчанию `~/.redmine-mcp-server` |
| `REDMINE_MCP_ATTACHMENT_PER_PART_CHARS` | Лимит текста на один `part` (например, один файл внутри ZIP) для `getAttachment`; по умолчанию `30000` символов. Параметр `partLimit` инструмента переопределяет это значение. |
| `REDMINE_MCP_ATTACHMENT_PER_ATTACHMENT_CHARS` | Суммарный лимит извлечённого текста на одно вложение в `getAttachment`; по умолчанию `50000` символов. Параметр `maxChars` инструмента переопределяет это значение. |
| `REDMINE_MCP_PAGINATION_DEFAULT_LIMIT` | Лимит страницы по умолчанию для list/search-инструментов; по умолчанию `25` |
| `REDMINE_MCP_PAGINATION_DEFAULT_OFFSET` | Offset по умолчанию для list/search-инструментов; по умолчанию `0` |
| `REDMINE_MCP_PAGINATION_MEMBERS_DEFAULT_LIMIT` | Лимит страницы по умолчанию для `listProjectMembers`; по умолчанию `100` |
| `REDMINE_MCP_TREE_DEFAULT_DEPTH` | Глубина по умолчанию для `getIssueTree`; по умолчанию `2` |
| `REDMINE_MCP_TREE_MAX_DEPTH` | Максимальная глубина `getIssueTree`; по умолчанию `5` |
| `REDMINE_MCP_TREE_MAX_ISSUES` | Максимум задач, загружаемых `getIssueTree`; по умолчанию `50` |
| `REDMINE_MCP_ANALYSIS_MAX_PAGES` | Максимум страниц Redmine, читаемых аналитическими инструментами; по умолчанию `5` |
| `REDMINE_MCP_ANALYSIS_PAGE_SIZE` | Размер страницы Redmine для аналитических инструментов; по умолчанию `100` |
| `REDMINE_MCP_ANALYSIS_TOP_ISSUES_LIMIT` | Максимум задач в top-списках аналитических ответов; по умолчанию `10` |
| `REDMINE_MCP_ANALYSIS_MAX_BLOCKER_DEPTH` | Максимальная глубина обхода для `getBlockerChain`; по умолчанию `10` |
| `REDMINE_MCP_ANALYSIS_MAX_BLOCKER_ISSUES` | Максимум задач, загружаемых `getBlockerChain`; по умолчанию `30` |
| `REDMINE_MCP_ANALYSIS_STALE_ISSUES_DEFAULT_DAYS_SINCE_UPDATE` | Значение `daysSinceUpdate` по умолчанию для `getStaleIssues`; по умолчанию `30` |
| `REDMINE_MCP_ANALYSIS_STALE_ISSUES_DEFAULT_LIMIT` | Лимит результатов по умолчанию для `getStaleIssues`; по умолчанию `25` |
| `REDMINE_MCP_ANALYSIS_STALE_ISSUES_MAX_LIMIT` | Максимальный лимит результатов для `getStaleIssues`; по умолчанию `100` |
| `REDMINE_MCP_EXTRACTION_PANDOC_ENABLED` | Включает использование Pandoc для DOCX, если бинарь найден в `PATH`; по умолчанию `true` |
| `REDMINE_MCP_EXTRACTION_PANDOC_PROBE_TIMEOUT_SECONDS` | Таймаут проверки доступности Pandoc при старте; по умолчанию `2` секунды |
| `REDMINE_MCP_EXTRACTION_PANDOC_CONVERSION_TIMEOUT_SECONDS` | Таймаут одного преобразования DOCX через Pandoc; по умолчанию `30` секунд |
| `REDMINE_MCP_EXTRACTION_LIMITS_MAX_DEPTH` | Максимальная глубина рекурсивной обработки вложенных документов и архивов; по умолчанию `1` |
| `REDMINE_MCP_EXTRACTION_LIMITS_MAX_TOTAL_PARTS` | Максимум текстовых/метаданных частей за одно извлечение; по умолчанию `100` |
| `REDMINE_MCP_EXTRACTION_LIMITS_MAX_TOTAL_BYTES` | Суммарный лимит прочитанных данных за одно извлечение; по умолчанию `52428800` байт |
| `REDMINE_MCP_EXTRACTION_LIMITS_MAX_ENTRY_BYTES` | Лимит одного entry внутри архива; по умолчанию `10485760` байт |
| `REDMINE_MCP_EXTRACTION_ZIP_MAX_ENTRIES_PER_ARCHIVE` | Максимум записей в одном ZIP-архиве; по умолчанию `100` |
| `REDMINE_MCP_EXTRACTION_TIKA_BODY_LIMIT_BYTES` | Лимит тела, передаваемого Tika fallback-парсеру; по умолчанию `5242880` байт |
| `REDMINE_MCP_EXTRACTION_TIKA_METADATA_MAX_FIELDS` | Максимум полей метаданных Tika в ответе; по умолчанию `40` |

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

## Проверка запуска

Перед подключением к AI-клиенту полезно убедиться, что JAR корректно стартует с теми же
переменными окружения, которые потом будут указаны в конфигурации клиента.

Linux/macOS:

```bash
REDMINE_URL=https://redmine.example.com REDMINE_API_KEY=your_key \
  java -jar build/libs/redmine-mcp-server.jar
```

Windows PowerShell:

```powershell
$env:REDMINE_URL="https://redmine.example.com"
$env:REDMINE_API_KEY="your_key"
java -jar .\build\libs\redmine-mcp-server.jar
```

Сервер работает через `stdio` и не открывает HTTP-порт: после успешного старта он молча
ждёт MCP-запросы через `stdin/stdout`. Признаком успешного старта служит отсутствие ошибок
в логе и отсутствие немедленного завершения процесса. Для остановки достаточно нажать
`Ctrl+C`.

### Логи

Логи пишутся в `${REDMINE_MCP_DATA_DIR:-~/.redmine-mcp-server}/logs/redmine-mcp-server.log`.
Файл ротируется по дате и размеру: `10MB`, хранение `30` дней, общий лимит `512MB`.

### Снимки задач

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
| Каждая текстовая часть `getAttachment.parts[]` | до 30 000 символов по умолчанию, дальше текст обрезается |
| Одно вложение в `getAttachment` суммарно | до 50 000 символов по умолчанию |
| ZIP-глубина | 1 уровень |
| ZIP-файлы | до 100 записей |
| ZIP-файл внутри архива | до 10 MB |
| ZIP-архив суммарно | до 50 MB извлечённых данных |
| `getIssueTree` | глубина до 5, максимум 50 задач |

`getIssue` и `getAttachment` поддерживают `responseProfile`. Профиль `default`
сохраняет обычную форму ответа и применяет компрессию только при превышении бюджета ответа.
Профиль `review` предназначен для code review реализации по задаче: полная issue всё равно
сохраняется на диск, а в ответе tool сохраняются review-релевантные поля — описание, человеческие
заметки, метаданные вложений и все revisions changeset-ов; verbose-история и тело commit-сообщений
опускаются. Профиль `full` сейчас эквивалентен `default` и оставлен как явный выбор полной формы
с защитной бюджетной компрессией.

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
│   ├── api/                                — стабильный MCP wire format: records, возвращаемые tools/services
│   │   ├── Issue.java
│   │   ├── AttachmentContent.java
│   │   ├── Project.java
│   │   └── ...                             — DTO ответов инструментов и аналитики
│   ├── client/
│   │   ├── RedmineClient.java              — обёртка над Redmine REST API
│   │   └── model/                          — raw DTO Redmine REST API, не экспортируются напрямую в MCP
│   │       ├── RedmineIssue.java
│   │       ├── RedmineAttachment.java
│   │       ├── RedmineProject.java
│   │       └── ...
│   ├── config/
│   │   ├── RedmineClientProperties.java   — url + apiKey из env
│   │   ├── RedmineMcpProperties.java      — все runtime-настройки redmine-mcp.*
│   │   ├── RedmineConfig.java             — RestClient
│   │   ├── McpServerConfig.java           — stdio MCP customizer с immediateExecution(true)
│   │   └── JsonConfig.java                — ObjectMapper для MCP JSON
│   ├── extraction/
│   │   ├── ExtractionPipeline.java        — document-to-text pipeline
│   │   ├── DocumentParser.java            — интерфейс парсеров
│   │   ├── FileTypeDetector.java          — определение типа файла
│   │   ├── PandocAvailability.java        — проба внешнего pandoc при старте
│   │   └── parser/
│   │       ├── PlainTextParser.java       — txt/log/csv/json/xml
│   │       ├── PdfTextParser.java         — PDF через PDFBox
│   │       ├── DocxTextParser.java        — DOCX через POI
│   │       ├── DocxPandocParser.java      — DOCX через Pandoc, если доступен
│   │       ├── XlsxTextParser.java        — XLSX через POI
│   │       ├── PptxTextParser.java        — PPTX через POI
│   │       ├── ZipParser.java             — ZIP с bounded recursion
│   │       ├── ImagePassthroughParser.java
│   │       ├── TikaTextFallbackParser.java
│   │       ├── TikaMetadataParser.java
│   │       └── BinaryFallbackParser.java
│   ├── service/
│   │   ├── IssueService.java              — бизнес-логика задач и mapping client.model -> api
│   │   ├── AttachmentService.java         — snapshot, скачивание и извлечение вложений
│   │   ├── IssueSnapshotService.java      — локальные снимки issue и вложений
│   │   ├── AnalysisService.java           — аналитика, риски, blocker chain
│   │   └── ...                            — сервисы проектов, wiki, поиска, справочников, трудозатрат
│   └── tools/
│       ├── AnalysisTools.java             — 7 MCP-инструментов аналитики и анализа рисков
│       ├── AttachmentTools.java           — 1 MCP-инструмент для файлов и контекста вложений
│       ├── IncidentPrompts.java           — MCP-промпт для расследования инцидентов
│       ├── IssueTools.java                — MCP-инструменты для задач
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
