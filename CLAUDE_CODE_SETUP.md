# Подключение Redmine MCP Server к Claude Code

Эта инструкция описывает только установку и подключение **Redmine MCP Server**. Предполагается, что Claude Code уже установлен, авторизация выполнена, а сетевой доступ к корпоративному Redmine настроен.

## Что понадобится

- Установленный и настроенный Claude Code.
- Доступ к корпоративному Redmine из той среды, где запускается Claude Code.
- JDK 25+ для сборки и запуска сервера.
- `REDMINE_URL` — базовый URL Redmine, например `https://redmine.example.com`.
- `REDMINE_API_KEY` — персональный API-ключ пользователя Redmine.

## 1. Собрать MCP-сервер

В корне репозитория:

```bash
./gradlew build
```

На Windows можно использовать:

```powershell
.\gradlew.bat build
```

Если JDK 25+ не является JDK по умолчанию, перед сборкой задайте `JAVA_HOME`.

Результат сборки:

```text
build/libs/redmine-mcp-server.jar
```

## 2. Получить параметры Redmine

`REDMINE_URL` — это адрес Redmine без пути к конкретной странице.

| В браузере | Значение `REDMINE_URL` |
|---|---|
| `https://redmine.example.com/projects/backend` | `https://redmine.example.com` |
| `http://192.168.1.50:3000/issues/123` | `http://192.168.1.50:3000` |
| `http://10.0.0.5/redmine/projects` | `http://10.0.0.5/redmine` |

`REDMINE_API_KEY` находится в Redmine: **Моя учетная запись** -> **Ключ доступа к API** -> **Показать**.

Если блок API-ключа не отображается, REST API может быть отключен администратором Redmine.

## 3. Проверить запуск сервера

Перед подключением к Claude Code полезно проверить, что JAR запускается с теми же переменными окружения.

Linux/macOS:

```bash
REDMINE_URL=https://redmine.example.com REDMINE_API_KEY=your_api_key \
  java -jar build/libs/redmine-mcp-server.jar
```

Windows PowerShell:

```powershell
$env:REDMINE_URL="https://redmine.example.com"
$env:REDMINE_API_KEY="your_api_key"
java -jar .\build\libs\redmine-mcp-server.jar
```

Сервер работает через `stdio`, поэтому после успешного старта он не открывает HTTP-порт и ждет MCP-запросы через stdin/stdout.
Логи пишутся в `${REDMINE_MCP_DATA_DIR:-~/.redmine-mcp-server}/logs/redmine-mcp-server.log`.

## 4. Подключить сервер к Claude Code

Рекомендуемый вариант — зарегистрировать сервер через CLI Claude Code:

```bash
claude mcp add --scope user \
  --env REDMINE_URL=https://redmine.example.com \
  --env REDMINE_API_KEY=your_api_key \
  -- redmine java -jar "C:/absolute/path/to/redmine-mcp-server/build/libs/redmine-mcp-server.jar"
```

Если `java` недоступна из окружения Claude Code, укажите полный путь к `java.exe`:

```bash
claude mcp add --scope user \
  --env REDMINE_URL=https://redmine.example.com \
  --env REDMINE_API_KEY=your_api_key \
  -- redmine "C:/Program Files/Java/jdk-25/bin/java.exe" -jar "C:/absolute/path/to/redmine-mcp-server/build/libs/redmine-mcp-server.jar"
```

Флаг `--scope user` добавляет сервер в пользовательскую конфигурацию. Без него сервер может быть добавлен только для текущего проекта, в зависимости от версии Claude Code.

Проверить регистрацию:

```bash
claude mcp list
```

Удалить регистрацию:

```bash
claude mcp remove --scope user redmine
```

## 5. Проверить в Claude Code

1. Перезапустите Claude Code после изменения MCP-конфигурации.
2. Выполните команду:

```text
/mcp
```

В списке должен быть сервер `redmine` со статусом подключения.

Примеры запросов:

```text
Покажи мои задачи в Redmine
```

```text
Найди открытые блокеры по проекту backend
```

## Доступные инструменты

Сервер предоставляет **32 read-only MCP tools**. Он не изменяет данные в Redmine.

Основные группы инструментов:

| Группа | Примеры |
|---|---|
| Пользователь | `getCurrentUser` |
| Проекты | `listProjects`, `getProject`, `listProjectMembers`, `listVersions` |
| Задачи | `listIssues`, `searchIssues`, `getIssue`, `getMyIssues`, `getIssueTree`, `getIssueHistory` |
| Вложения | `getAttachmentFile`, `getAttachmentContext` |
| Wiki | `listWikiPages`, `getWikiPage` |
| Трудозатраты | `listTimeEntries`, `getMyTimeEntries` |
| Справочники | `listQueries`, `listStatuses`, `listTrackers`, `listPriorities`, `listIssueCategories`, `listTimeEntryActivities` |
| Аналитика и контекст | `getProjectSummary`, `getReleaseRisks`, `getIssueFullContext`, `getBlockerChain` |

Полный список инструментов см. в [README.md](README.md).

## Устранение проблем

**`java` не найден**

Проверьте `JAVA_HOME` и `Path`. Для MCP-конфигурации можно указать полный путь к `java.exe`.

**MCP-сервер не подключается**

Проверьте абсолютный путь к `redmine-mcp-server.jar`, наличие JDK 25+ и корректность переменных `REDMINE_URL` / `REDMINE_API_KEY`.

**Redmine возвращает 401 или connection refused**

Проверьте доступность Redmine и API-ключ:

```bash
curl -H "X-Redmine-API-Key: your_api_key" https://redmine.example.com/users/current.json
```

**Сервер добавлен, но не появился в Claude Code**

Полностью перезапустите Claude Code и проверьте список серверов командой `claude mcp list`.
