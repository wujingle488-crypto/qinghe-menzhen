# 青禾门诊 · 智能问诊

教学演示用的门诊问诊系统。用户注册登录后描述症状，系统多轮追问，从**本仓库自带的知识库**检索资料，再给出参考判断和库内用药提示。胸痛、大出血、叫不醒等危险情况只建议急诊，不开药。

这里不是真实诊疗，也不能当作电子处方。

## 拉下来之后会看到什么

按下面的步骤在本机启动后：

- 打开 `http://127.0.0.1:5173`，先看到登录页。注册一个账号即可进入。
- 三个页面：问诊、就诊卡、知识库。右上角头像可以退出。每个账号只能看到自己的问诊记录和就诊卡。
- 知识库里的文章、分类和封面，与当前教学版本一致。后端第一次连接空数据库时，会把这些文章写入本地 MySQL，不需要再从网上抓知识。
- 问诊用的向量索引也在本机生成：脚本读取仓库里的 `知识库/*.md`，下载一次中文嵌入模型到本机缓存，写入 `知识库/index/chroma-data`。

新环境里的问诊历史和就诊卡是空的。这些数据按账号存在你自己的 MySQL 里，不会跟着仓库走。

没有 DeepSeek 密钥时，页面和知识库仍然完整。问诊回答会走本地规则降级，而不是调用大模型。

## 需要的软件

| 软件 | 版本 | 缺失时 |
| --- | --- | --- |
| JDK | 21 | `winget` 安装 Microsoft OpenJDK 21 |
| Maven | 3.9 或更高 | 下载到 `%LOCALAPPDATA%\QingheClinic\tools` |
| Node.js | 18 或更高 | `winget` 安装 Node.js LTS |
| Python | 3.10 或更高 | `winget` 安装 Python 3.12 |
| MySQL | 8 | 下载便携版到上述 tools 目录并自动起库；失败再试 winget |

启动脚本会检测 `java` / `mvn` / `npm` / `python` / `mysql`，没有就自动装。首次可能较久，并可能弹出 UAC（winget）。本机需能访问外网，且已有 `winget`（微软「应用安装程序」）。

已装好的工具会直接复用，不会重复安装。Neo4j 和 Elasticsearch 不是启动页面所必需的。

## 让别人或 AI 直接启动

在仓库根目录执行。

### 1. 准备密钥（可选）

```bash
copy .env.example .env
```

用编辑器打开 `.env`，把 `LLM_API_KEY` 写成你自己的 DeepSeek 密钥。没有密钥就留空，系统仍能启动。`.env` 不会被提交。

若本机 MySQL 的 root 有密码，可在 `.env` 写 `MYSQL_ROOT_PASSWORD=你的密码`，脚本会用来自动建库。便携 MySQL（脚本自动下载的那种）root 为空密码，会直接执行 `scripts/init-mysql.sql`。

### 2. Windows 一键启动

有两套脚本，按场景选：

| 脚本 | 适用 |
| --- | --- |
| `一键启动-通用.bat` / `scripts/start-portable.ps1` | **推荐给别人用**。不绑定本机目录；缺啥装啥。 |
| `一键启动.bat` / `scripts/start.ps1` | 本机专用。优先用 `D:\DevelopTools\...`，仍缺则同样自动安装。 |

通用启动（任意电脑）：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/start-portable.ps1
```

或双击根目录 `一键启动-通用.bat`。

本机启动（含 DevelopTools 回退）：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/start.ps1
```

或双击根目录 `一键启动.bat`。

脚本会依次：

1. 检测并自动安装缺失的 `java` / `mvn` / `npm` / `python` / `mysql`。
2. 确认（或自动创建）MySQL 库 `commerce_cs`。
3. 在 `commerce-cs-agent/web` 执行 `npm install`（已有 `node_modules` 时跳过）。
4. 安装 `知识库/index/requirements.txt`，并把 `知识库` 目录下的 Markdown 切块、嵌入，写入本地 Chroma。模型是 `BAAI/bge-small-zh-v1.5`，第一次会下载到本机缓存。
5. 启动本地向量库 `127.0.0.1:8000` 和嵌入服务 `127.0.0.1:8001`。
6. 编译并启动后端 `http://127.0.0.1:8082`。后端启动时自动把疾病、药品白名单、红旗和知识文章写入 MySQL。
7. 启动前端 `http://127.0.0.1:5173`。

浏览器打开 `http://127.0.0.1:5173`。看到登录页就说明前端和后端已经接上。注册后进入问诊，点「知识库」应能看到常见疾病、症状表现、用药指南、检查检验等分类和文章。

### 3. 不用脚本时，按这个顺序自己启动

以下命令的当前目录都是仓库根目录，除非另写了 `cd`。

```bash
mysql -u root -p < scripts/init-mysql.sql
copy .env.example .env
```

把 `.env` 里的 `LLM_API_KEY` 填好后，在同一个终端里让后续命令读到它。PowerShell：

```powershell
Get-Content .env | ForEach-Object {
  if ($_ -match '^\s*LLM_API_KEY=(.*)$') { $env:LLM_API_KEY = $Matches[1].Trim() }
}
```

处理知识库并启动向量检索（第一次会下载嵌入模型）：

```bash
python -m pip install -r 知识库/index/requirements.txt
python 知识库/index/index_kb.py
chroma run --path 知识库/index/chroma-data --host 127.0.0.1 --port 8000
```

另开一个终端：

```bash
python 知识库/index/embed_server.py
```

再开一个终端启动后端。它会创建表，并在库为空时写入全部教学知识：

```bash
cd commerce-cs-agent
mvn -pl domain,agent-server -am install -DskipTests
mvn -f agent-server/pom.xml spring-boot:run
```

再开一个终端启动前端：

```bash
cd commerce-cs-agent/web
npm install
npm run dev
```

前端把 `/cs-api` 代理到 `http://127.0.0.1:8082`。不要改这个端口，除非后端也一起改。

## 怎么确认和本地演示一致

1. `http://127.0.0.1:8082/api/health` 返回状态正常，数据库为 up。
2. 未登录打开 `http://127.0.0.1:5173` 只能看到登录/注册。
3. 注册两个不同用户。甲用户问一句后，乙用户的历史对话里没有甲的记录。
4. 知识库中能看到这些教学文章，例如「普通感冒和流感不要混为一谈」「流感更常有全身症状」「血常规检查解读」「常见退热药的使用注意」「日常起居与饮食调理」。
5. 热门推荐是四张带封面的卡片，分类里检查检验、用药指南、中医调理都有内容。

## 目录

| 路径 | 作用 |
| --- | --- |
| `commerce-cs-agent/web` | React 页面：登录、问诊、就诊卡、知识库 |
| `commerce-cs-agent/agent-server` | Spring Boot 接口、问诊编排、登录 |
| `commerce-cs-agent/domain` | 数据库实体 |
| `知识库/*.md` | 已下载到仓库里的公开科普原文 |
| `知识库/index` | 把上文切块并写入本地 Chroma |
| `scripts/start.ps1` | Windows 启动脚本 |
| `scripts/init-mysql.sql` | 建库和账号 |
| `项目范围说明.md` | 做什么、不做什么 |

问诊链路是：大模型理解问题，需要时检索本地知识，再由 Java 做红旗和药品白名单检查。模型或向量库不可用时会降级，仍然不会推荐库外的药。
