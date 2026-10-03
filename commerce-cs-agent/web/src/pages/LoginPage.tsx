import { useState, type FormEvent } from "react";
import { api, errorText, setAuthToken, type Account } from "../api";

export function LoginPage({ onEnter }: { onEnter: (account: Account) => void }) {
  const [mode, setMode] = useState<"login" | "register">("login");
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setMessage("");
    try {
      const path = mode === "login" ? "/cs-api/api/auth/login" : "/cs-api/api/auth/register";
      const response = await api(path, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ username, password, confirm })
      });
      if (!response.ok) throw new Error(await errorText(response));
      const account = (await response.json()) as Account & { token: string };
      setAuthToken(account.token);
      onEnter({ id: account.id, username: account.username });
    } catch (err) {
      setMessage(err instanceof Error ? err.message : "登录失败");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="qh-login">
      <img className="qh-leaf qh-leaf-a" src="/brand/leaf_bg.png" alt="" />
      <img className="qh-leaf qh-leaf-b" src="/brand/leaf_bg.png" alt="" />
      <form className="qh-login-card" onSubmit={(event) => void submit(event)}>
        <div className="qh-brand">
          <img src="/brand/logo_heart.svg" alt="" />
          <div>
            <strong>青禾门诊</strong>
            <span>智能问诊 · 教学参考</span>
          </div>
        </div>
        <h1>{mode === "login" ? "登录" : "注册"}</h1>
        <p>每个账号只看自己的问诊记录和就诊卡。</p>
        <div className="qh-login-tabs">
          <button type="button" className={mode === "login" ? "on" : ""} onClick={() => { setMode("login"); setMessage(""); }}>登录</button>
          <button type="button" className={mode === "register" ? "on" : ""} onClick={() => { setMode("register"); setMessage(""); }}>注册</button>
        </div>
        <label>
          用户名
          <input value={username} autoComplete="username" placeholder="例如 张三" onChange={(event) => setUsername(event.target.value)} />
        </label>
        <label>
          密码
          <input type="password" value={password} autoComplete={mode === "login" ? "current-password" : "new-password"} placeholder="至少 6 位" onChange={(event) => setPassword(event.target.value)} />
        </label>
        {mode === "register" ? (
          <label>
            确认密码
            <input type="password" value={confirm} autoComplete="new-password" placeholder="再输入一次" onChange={(event) => setConfirm(event.target.value)} />
          </label>
        ) : null}
        {message ? <p className="qh-login-error">{message}</p> : null}
        <button type="submit" className="qh-login-submit" disabled={busy}>
          {busy ? "请稍候…" : mode === "login" ? "登录" : "注册并进入"}
        </button>
      </form>
    </div>
  );
}
