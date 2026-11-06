import { useState, type FormEvent } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { ShieldCheck } from "lucide-react";
import { authApi } from "./api";
import { useAuth } from "./AuthContext";
import { ApiError } from "../../lib/http";

function AuthFrame({title,description,children}:{title:string;description:string;children:React.ReactNode}) {
  return <section className="auth-frame"><div className="auth-brand">Stockly<span>.</span></div>{import.meta.env.MODE === "stk005-demo"&&<div className="demo-notice" role="status"><strong>Simulación STK-005</strong><span>Datos ficticios · Cambios locales no persistentes.</span></div>}<div className="panel form-panel auth-panel"><div className="auth-icon"><ShieldCheck size={23}/></div><h1>{title}</h1><p className="subtitle">{description}</p>{children}</div><p className="auth-footnote">Acceso seguro a Stockly</p></section>;
}
function FormError({value}:{value:unknown}) {
  if(!value) return null;
  return <div className="notice error" role="alert">{value instanceof Error?value.message:"No se pudo completar la solicitud."}</div>;
}

export function LoginPage() {
  const [identifier,setIdentifier]=useState(""); const [password,setPassword]=useState(""); const [error,setError]=useState<unknown>(); const [busy,setBusy]=useState(false);
  const {refresh}=useAuth(); const navigate=useNavigate();
  async function submit(event:FormEvent) {
    event.preventDefault(); setBusy(true); setError(undefined);
    try {
      const result=await authApi.login(identifier,password);
      if(result.status==="DEVICE_CODE_REQUIRED" && result.challengeId) { sessionStorage.setItem("stockly.challenge",result.challengeId); navigate("/verify-device"); }
      else { await refresh(); navigate("/"); }
    } catch(e) { setError(e); } finally { setBusy(false); }
  }
  return <AuthFrame title="Iniciar sesión" description="Ingresa con tu usuario o correo y contraseña.">
    <form className="form-grid single-column" onSubmit={submit}>
      <label className="field"><span>Usuario o correo</span><input autoComplete="username" value={identifier} onChange={e=>setIdentifier(e.target.value)} required maxLength={254}/></label>
      <label className="field"><span>Contraseña</span><input type="password" autoComplete="current-password" value={password} onChange={e=>setPassword(e.target.value)} required maxLength={128}/></label>
      <FormError value={error}/><button className="button primary" disabled={busy}>{busy?"Ingresando…":"Ingresar"}</button>
    </form><Link className="auth-link" to="/forgot-password">¿Olvidaste tu contraseña?</Link>
  </AuthFrame>;
}

export function VerifyDevicePage() {
  const [code,setCode]=useState(""); const [remember,setRemember]=useState(false); const [error,setError]=useState<unknown>(); const [busy,setBusy]=useState(false);
  const challenge=sessionStorage.getItem("stockly.challenge"); const {refresh}=useAuth(); const navigate=useNavigate();
  async function submit(event:FormEvent) {
    event.preventDefault(); if(!challenge)return; setBusy(true);setError(undefined);
    try { await authApi.verify(challenge,code,remember);sessionStorage.removeItem("stockly.challenge");await refresh();navigate("/"); }
    catch(e){setError(e);}finally{setBusy(false);}
  }
  return <AuthFrame title="Verifica este dispositivo" description="Te enviamos un código de 6 dígitos al correo de tu cuenta. Vence en 10 minutos.">
    {!challenge?<div className="notice error" role="alert">No hay una verificación pendiente. <Link to="/login">Volver a ingresar</Link></div>:<form className="form-grid single-column" onSubmit={submit}>
      <label className="field"><span>Código de verificación</span><input inputMode="numeric" autoComplete="one-time-code" pattern="[0-9]{6}" minLength={6} maxLength={6} value={code} onChange={e=>setCode(e.target.value.replace(/\D/g,""))} required/></label>
      <label className="remember-device"><input type="checkbox" checked={remember} onChange={e=>setRemember(e.target.checked)}/> Recordar este dispositivo por 30 días</label>
      <FormError value={error}/><button className="button primary" disabled={busy}>{busy?"Verificando…":"Verificar código"}</button>
      <Link className="auth-link" to="/login">Cancelar</Link>
    </form>}
  </AuthFrame>;
}

export function RecoveryPage() {
  const [email,setEmail]=useState("");const [error,setError]=useState<unknown>();const [done,setDone]=useState(false);const [busy,setBusy]=useState(false);
  async function submit(event:FormEvent){event.preventDefault();setBusy(true);setError(undefined);try{await authApi.recovery(email);setDone(true);}catch(e){setError(e);}finally{setBusy(false);}}
  return <AuthFrame title="Recuperar acceso" description="Ingresa el correo verificado asociado a tu cuenta.">
    {done?<div className="notice info" role="status">Si el correo corresponde a una cuenta activa y verificada, recibirás un enlace válido por 30 minutos.</div>:<form className="form-grid single-column" onSubmit={submit}>
      <label className="field"><span>Correo electrónico</span><input type="email" autoComplete="email" value={email} onChange={e=>setEmail(e.target.value)} required maxLength={254}/></label>
      <FormError value={error}/><button className="button primary" disabled={busy}>{busy?"Enviando…":"Enviar enlace"}</button>
    </form>}
    <p className="auth-help">Si tu contraseña temporal venció antes del primer ingreso, solicita otra al administrador de tu empresa. Para el administrador inicial, contacta al equipo de Stockly.</p>
    <Link className="auth-link" to="/login">Volver a iniciar sesión</Link>
  </AuthFrame>;
}

export function CompleteRecoveryPage() {
  const [params]=useSearchParams();const [password,setPassword]=useState("");const [confirm,setConfirm]=useState("");const [error,setError]=useState<unknown>();const [done,setDone]=useState(false);const [busy,setBusy]=useState(false);
  const token=params.get("token")||"";
  async function submit(event:FormEvent){event.preventDefault();if(password!==confirm){setError(new Error("Las contraseñas no coinciden."));return;}setBusy(true);setError(undefined);try{await authApi.completeRecovery(token,password);setDone(true);}catch(e){setError(e);}finally{setBusy(false);}}
  return <AuthFrame title="Crea una contraseña nueva" description="La contraseña debe tener entre 15 y 128 caracteres.">
    {done?<div className="notice success" role="status">Contraseña actualizada. <Link to="/login">Iniciar sesión</Link></div>:<form className="form-grid single-column" onSubmit={submit}>
      <label className="field"><span>Nueva contraseña</span><input type="password" autoComplete="new-password" value={password} onChange={e=>setPassword(e.target.value)} required minLength={15} maxLength={128}/></label>
      <label className="field"><span>Repite la contraseña</span><input type="password" autoComplete="new-password" value={confirm} onChange={e=>setConfirm(e.target.value)} required minLength={15} maxLength={128}/></label>
      <FormError value={error}/><button className="button primary" disabled={busy||!token}>{busy?"Actualizando…":"Cambiar contraseña"}</button>
    </form>}
  </AuthFrame>;
}

export function FirstPasswordChangePage() {
  const [password,setPassword]=useState("");const [confirm,setConfirm]=useState("");const [error,setError]=useState<unknown>();const [busy,setBusy]=useState(false);
  const {refresh}=useAuth();const navigate=useNavigate();
  async function submit(event:FormEvent){event.preventDefault();if(password!==confirm){setError(new Error("Las contraseñas no coinciden."));return;}setBusy(true);setError(undefined);try{await authApi.firstChange(password);await refresh();navigate("/");}catch(e){setError(e);}finally{setBusy(false);}}
  return <AuthFrame title="Cambia tu contraseña temporal" description="Debes cambiarla para continuar. Usa entre 15 y 128 caracteres.">
    <form className="form-grid single-column" onSubmit={submit}>
      <label className="field"><span>Nueva contraseña</span><input type="password" autoComplete="new-password" value={password} onChange={e=>setPassword(e.target.value)} required minLength={15} maxLength={128}/></label>
      <label className="field"><span>Repite la contraseña</span><input type="password" autoComplete="new-password" value={confirm} onChange={e=>setConfirm(e.target.value)} required minLength={15} maxLength={128}/></label>
      <FormError value={error}/><button className="button primary" disabled={busy}>{busy?"Guardando…":"Guardar contraseña"}</button>
    </form>
  </AuthFrame>;
}
