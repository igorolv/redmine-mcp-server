# Подключение Redmine MCP Server к Qwen Code

Пошаговая инструкция по настройке AI-ассистента [Qwen Code](https://github.com/QwenLM/qwen-code) с доступом к вашему Redmine через MCP-сервер.

## Содержание

1. [Регистрация на OpenRouter и получение API-ключа](#1-регистрация-на-openrouter-и-получение-api-ключа)
2. [Установка Qwen Code CLI](#2-установка-qwen-code-cli)
3. [Подключение OpenRouter к Qwen Code](#3-подключение-openrouter-к-qwen-code)
4. [Сборка MCP-сервера](#4-сборка-mcp-сервера)
5. [Подключение MCP-сервера к Qwen Code](#5-подключение-mcp-сервера-к-qwen-code)
6. [Проверка работы](#6-проверка-работы)

---

## 1. Регистрация на OpenRouter и получение API-ключа

[OpenRouter](https://openrouter.ai) — это сервис, предоставляющий единый API для доступа к различным AI-моделям, включая бесплатные.

### Регистрация

1. Откройте https://openrouter.ai
2. Нажмите **Sign up**
3. Войдите через **Google**, **GitHub** или **email**
4. Если выбрали email — проверьте почту и подтвердите регистрацию по ссылке из письма

### Получение API-ключа

1. После входа перейдите на страницу https://openrouter.ai/settings/keys
2. Нажмите **Create Key**
3. Укажите имя ключа (например, `qwen-code`) и нажмите **Create**
4. **Скопируйте ключ сразу** — он отображается только один раз. Ключ начинается с `sk-or-v1-...`

> Храните ключ в безопасном месте. Не публикуйте его в открытых репозиториях.
>
> Вы можете создавать несколько ключей — например, отдельный для каждого проекта или инструмента. Это удобно для отслеживания расходов и отзыва доступа при необходимости.

### Пополнение баланса

Для использования платных моделей на OpenRouter необходимо пополнить баланс. Это можно сделать на странице https://openrouter.ai/settings/credits. Если стандартные способы оплаты недоступны — кредиты OpenRouter можно приобрести, например, на [plati.market](https://plati.market) (не реклама).

---

## 2. Установка Qwen Code CLI

[Qwen Code](https://github.com/QwenLM/qwen-code) — AI-ассистент для работы с кодом в терминале, аналог Claude Code.

### Требования

- **Node.js 20** или новее — скачайте с https://nodejs.org

Проверьте установку Node.js:

```bash
node --version
```

### Установка Qwen Code

Запустите cmd или PowerShell от администратора:

```bash
npm install -g @qwen-code/qwen-code@latest
```

### Проверка

```bash
qwen --version
```

---

## 3. Подключение OpenRouter к Qwen Code

Qwen Code читает настройки подключения из переменных среды. Нужно создать три переменные для текущего пользователя:

| Переменная | Значение |
|------------|----------|
| `OPENAI_API_KEY` | `sk-or-v1-ваш-ключ` |
| `OPENAI_BASE_URL` | `https://openrouter.ai/api/v1` |
| `OPENAI_MODEL` | `qwen/qwen3-coder` |

> Для бесплатной модели используйте `qwen/qwen3-coder:free` вместо `qwen/qwen3-coder`.

### Вариант А — через графический интерфейс

1. Откройте **Параметры** → **Система** → **О системе** → **Дополнительные параметры системы**
2. Нажмите **Переменные среды**
3. В разделе **Переменные среды пользователя** нажмите **Создать** и добавьте каждую из трёх переменных из таблицы выше
4. Нажмите **OK** во всех открытых окнах

### Вариант Б — через командную строку

Откройте cmd и выполните:

```cmd
setx OPENAI_API_KEY "sk-or-v1-ваш-ключ"
setx OPENAI_BASE_URL "https://openrouter.ai/api/v1"
setx OPENAI_MODEL "qwen/qwen3-coder"
```

> Команда `setx` сохраняет переменные в реестре Windows. Они будут доступны во всех новых окнах терминала.

### Проверка

Откройте **новое** окно терминала и запустите Qwen Code:

```bash
qwen
```

---

## 4. Сборка MCP-сервера

### Установка Java 25

MCP-сервер требует **Java 25** или новее. Скачайте JDK с https://www.oracle.com/europe/java/technologies/downloads/#jdk26-windows или любого другого поставщика OpenJDK. При установке убедитесь, что переменная среды `JAVA_HOME` указывает на каталог JDK, а `%JAVA_HOME%\bin` добавлен в `Path`.

Проверьте:

```bash
java -version
```

### Git

Убедитесь, что Git установлен и доступен в терминале. Если нет — скачайте с https://git-scm.com/downloads/win.

```bash
git --version
```

### Клонирование репозитория

```bash
git clone https://github.com/your-org/redmine-mcp-server.git
cd redmine-mcp-server
```

### Сборка

```bash
gradlew.bat bootJar
```

После успешной сборки JAR-файл появится по пути:

```
build/libs/redmine-mcp-server.jar
```

### Получение API-ключа Redmine

1. Войдите в ваш Redmine
2. Нажмите **Моя учётная запись** (вверху справа)
3. На правой панели найдите раздел **Ключ доступа к API**
4. Нажмите **Показать** и скопируйте ключ

---

## 5. Подключение MCP-сервера к Qwen Code

Создайте (или отредактируйте, если уже есть) файл `C:\Users\<ваше_имя>\.qwen\settings.json` со следующим содержимым:

```json
{
  "mcpServers": {
    "redmine": {
      "command": "java",
      "args": [
        "-jar",
        "C:/полный/путь/к/redmine-mcp-server/build/libs/redmine-mcp-server.jar"
      ],
      "env": {
        "REDMINE_URL": "http://адрес-вашего-redmine",
        "REDMINE_API_KEY": "ваш-redmine-api-ключ"
      }
    }
  }
}
```

> Секция `env` нужна только если переменные `REDMINE_URL` и `REDMINE_API_KEY` не заданы в системе. Если вы уже настроили их как переменные среды пользователя — секцию `env` можно не указывать.
>
> Если Qwen Code не показывает сервер как подключенный, в первую очередь попробуйте указать полный путь к `java.exe` вместо `"java"`. На некоторых Windows-машинах `java` доступна в обычном терминале, но недоступна в окружении, из которого Qwen запускает MCP-сервер.

### Регистрация и удаление через CLI

Вместо ручного редактирования `settings.json` можно управлять MCP-сервером через CLI Qwen Code.

**Зарегистрировать сервер:**

```bash
qwen mcp add --scope user -e REDMINE_URL=http://адрес-вашего-redmine -e REDMINE_API_KEY=ваш-redmine-api-ключ redmine "C:/Program Files/Java/jdk-26/bin/java.exe" -jar "C:/полный/путь/к/redmine-mcp-server/build/libs/redmine-mcp-server.jar"
```

**Проверить статус:**

```bash
qwen mcp list
```

**Удалить сервер из конфигурации:**

```bash
qwen mcp remove --scope user redmine
```

**Пример полного `settings.json`** со всеми настройками:

```json
{
  "modelProviders": {
    "openai": [
      {
        "id": "qwen/qwen3-coder",
        "name": "Qwen3-Coder via OpenRouter",
        "baseUrl": "https://openrouter.ai/api/v1",
        "envKey": "OPENROUTER_API_KEY"
      },
      {
        "id": "qwen/qwen3-coder:free",
        "name": "Qwen3-Coder FREE via OpenRouter",
        "baseUrl": "https://openrouter.ai/api/v1",
        "envKey": "OPENROUTER_API_KEY"
      }
    ]
  },
  "env": {
    "OPENROUTER_API_KEY": "sk-or-v1-ваш-ключ"
  },
  "security": {
    "auth": {
      "selectedType": "openai"
    }
  },
  "model": {
    "name": "qwen/qwen3-coder"
  },
  "mcpServers": {
    "redmine": {
      "command": "C:/Program Files/Java/jdk-26/bin/java.exe",
      "args": [
        "-jar",
        "C:/projects/redmine-mcp-server/build/libs/redmine-mcp-server.jar"
      ],
      "env": {
        "REDMINE_URL": "http://192.168.1.100",
        "REDMINE_API_KEY": "a1b2c3d4e5f6..."
      }
    }
  }
}
```

> На Windows используйте прямые слеши (`/`) или двойные обратные (`\\`) в путях.

---

## 6. Проверка работы

1. Запустите Qwen Code в папке с вашим проектом:

```bash
cd /путь/к/вашему/проекту
qwen
```

2. Убедитесь, что MCP-сервер подключился — в логе запуска должно быть видно подключение к `redmine`.

3. Попробуйте задать вопросы, связанные с Redmine:

```
Покажи мои задачи в Redmine
```

```
По каким рабочим дням в марте я внёс трудозатраты меньше 8 часов?
```

Qwen Code вызовет инструменты MCP-сервера (`getMyIssues`, `getMyTimeEntries` и др.) и покажет результат.

### Доступные инструменты

MCP-сервер предоставляет инструменты только для **чтения** — он не может изменять данные в Redmine.

| Группа | Инструменты |
|--------|-------------|
| Пользователь | `getCurrentUser` |
| Проекты | `listProjects`, `getProject`, `listProjectMembers`, `listVersions` |
| Задачи | `listIssues`, `searchIssues`, `getIssue`, `getMyIssues` |
| Поиск | `searchAll` |
| Вложения | `listAttachments`, `getAttachmentContent`, `getImageAttachment` |
| Wiki | `listWikiPages`, `getWikiPage` |
| Трудозатраты | `listTimeEntries`, `getMyTimeEntries` |
| Справочники | `listQueries`, `listStatuses`, `listTrackers`, `listPriorities`, `listIssueCategories`, `listTimeEntryActivities` |

---

## Устранение проблем

**`java` не найден в терминале после установки**
Закройте и откройте терминал заново. Проверьте `JAVA_HOME` и `Path`.

**MCP-сервер не подключается**
Проверьте по шагам:

1. Убедитесь, что путь к JAR-файлу указан правильно и файл существует. В текущем проекте после сборки получается файл `build/libs/redmine-mcp-server.jar`.
2. Если в конфигурации указано `"command": "java"`, попробуйте заменить его на полный путь к `java.exe`, например:

```json
"command": "C:/Program Files/Java/jdk-26/bin/java.exe"
```

3. Попробуйте вручную запустить ровно тот же JAR с теми же переменными окружения. Для PowerShell:

```powershell
$env:REDMINE_URL="<REDMINE_URL>"; $env:REDMINE_API_KEY="<REDMINE_API_KEY>"; & "<полный-путь-к-java.exe-или-java>" -jar "<полный-путь-к-redmine-mcp-server.jar>"
```

Например:

```powershell
$env:REDMINE_URL="https://redmine.example.com"; $env:REDMINE_API_KEY="0123456789abcdef"; & "C:/Program Files/Java/jdk-26/bin/java.exe" -jar "C:/projects/redmine-mcp-server/build/libs/redmine-mcp-server.jar"
```

Что должно получиться:

- В консоли должны появиться стартовые строки приложения, например с `Starting RedmineMcpServerApplication`.
- Процесс не должен завершаться сразу с ошибкой.
- В `stdio`-режиме сервер после успешного старта просто остается запущенным и ждет MCP-запросы по stdin/stdout.

**Нет доступа к Redmine**
Проверьте, что `REDMINE_URL` и `REDMINE_API_KEY` верны:

```bash
curl -H "X-Redmine-API-Key: ваш-ключ" http://адрес-redmine/users/current.json
```

Должен вернуться JSON с данными вашего пользователя.
