import { useState, type FormEvent } from "react";
import { api, errorText, setAuthToken, type Account } from "../api";

function FieldIcon({ name }: { name: string }) {
  return (
    <span
      className="qh-login-ico"
      style={{ maskImage: `url("/brand/${name}")`, WebkitMaskImage: `url("/brand/${name}")` }}
      aria-hidden="true"
    />
  );
}

export function LoginPage({ onEnter }: { onEnter: (account: Account) => void }) {
  const [mode, setMode] = useState<"login" | "register">(
    window.location.hash === "#register" ? "register" : "login"
  );
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirm, setShowConfirm] = useState(false);
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
      setMessage(err instanceof Error ? err.message : mode === "login" ? "登录失败" : "注册失败");
    } finally {
      setBusy(false);
    }
  }

  function switchMode(next: "login" | "register") {
    setMode(next);
    window.location.hash = next === "register" ? "register" : "";
    setMessage("");
    setShowPassword(false);
    setShowConfirm(false);
  }

  const register = mode === "register";

  return (
    <div className="qh-login">
      <div className="qh-login-glow" />
      <div className="qh-login-hospital" />
      <div className="qh-login-leaves qh-login-leaves-l" />
      <div className="qh-login-leaves qh-login-leaves-r" />
      <svg className="qh-login-wave" viewBox="0 0 1440 120" preserveAspectRatio="none" aria-hidden="true">
        <path d="M0 72c180-28 320 18 480 8 210-14 330-48 520-36 140 8 280 42 440 18v58H0Z" fill="#eafbfa" />
        <path d="M0 92c200-22 360 10 540 2 190-8 310-32 470-22 150 10 270 28 430 8v40H0Z" fill="#fff" opacity=".92" />
      </svg>

      <p className="qh-login-script qh-login-script-tr">
        科技守护健康
        <span className="qh-login-heart" aria-hidden="true" />
      </p>

      <div className="qh-login-stage">
        <section className="qh-login-promo">
          <div className="qh-brand qh-login-brand">
            <img src="/brand/logo_heart.svg" alt="" />
            <div>
              <strong>青禾门诊</strong>
              <span>智能问诊 · 教学参考</span>
            </div>
          </div>
          <h1 className="qh-login-title">
            让 AI 陪伴您的
            <br />
            健康每一步
            <img className="qh-login-title-leaf" src="/brand/mint_leaf.svg" alt="" />
          </h1>
          <p className="qh-login-motto">专业 · 智能 · 贴心</p>
          <div className="qh-login-hero">
            <span className="qh-login-orb" aria-hidden="true" />
            <img src="/brand/ai_robot.png" alt="青禾门诊智能助手" />
          </div>
          <p className="qh-login-script qh-login-script-bl">
            关爱健康 · 从问诊开始
            <span className="qh-login-heart" aria-hidden="true" />
          </p>
        </section>

        <form className="qh-login-card" onSubmit={(event) => void submit(event)}>
          <div className="qh-brand qh-login-brand">
            <img src="/brand/logo_heart.svg" alt="" />
            <div>
              <strong>青禾门诊</strong>
              <span>智能问诊 · 教学参考</span>
            </div>
          </div>
          <h2>{register ? "注册" : "登录"}</h2>
          <div className="qh-login-tabs" role="tablist">
            <button type="button" className={register ? "" : "on"} onClick={() => switchMode("login")}>
              登录
            </button>
            <button type="button" className={register ? "on" : ""} onClick={() => switchMode("register")}>
              注册
            </button>
          </div>
          <label className="qh-login-field">
            <span>
              <FieldIcon name="user.svg" />
              用户名
            </span>
            <input
              value={username}
              autoComplete="username"
              placeholder="例如 张三"
              onChange={(event) => setUsername(event.target.value)}
            />
          </label>
          <label className="qh-login-field">
            <span>
              <FieldIcon name="lock.svg" />
              密码
            </span>
            <span className="qh-login-input">
              <input
                type={showPassword ? "text" : "password"}
                value={password}
                autoComplete={register ? "new-password" : "current-password"}
                placeholder="至少 6 位"
                onChange={(event) => setPassword(event.target.value)}
              />
              <button
                type="button"
                className="qh-login-eye"
                aria-label={showPassword ? "隐藏密码" : "显示密码"}
                onClick={() => setShowPassword((value) => !value)}
              >
                <FieldIcon name={showPassword ? "eye.svg" : "eye_off.svg"} />
              </button>
            </span>
          </label>
          {register ? (
            <label className="qh-login-field">
              <span>
                <FieldIcon name="lock.svg" />
                确认密码
              </span>
              <span className="qh-login-input">
                <input
                  type={showConfirm ? "text" : "password"}
                  value={confirm}
                  autoComplete="new-password"
                  placeholder="再输入一次"
                  onChange={(event) => setConfirm(event.target.value)}
                />
                <button
                  type="button"
                  className="qh-login-eye"
                  aria-label={showConfirm ? "隐藏确认密码" : "显示确认密码"}
                  onClick={() => setShowConfirm((value) => !value)}
                >
                  <FieldIcon name={showConfirm ? "eye.svg" : "eye_off.svg"} />
                </button>
              </span>
            </label>
          ) : null}
          {message ? <p className="qh-login-error">{message}</p> : null}
          <button type="submit" className="qh-login-submit" disabled={busy}>
            <FieldIcon name="arrow_right.svg" />
            {busy ? "请稍候…" : register ? "注册" : "登录"}
          </button>
        </form>
      </div>
    </div>
  );
}
