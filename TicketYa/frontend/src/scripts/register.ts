import gsap from 'gsap';
import {getSession,safeReturn} from './session';
const form=document.querySelector<HTMLFormElement>('#register-form')!;
const input=(id:string)=>document.getElementById(id) as HTMLInputElement;
const nombre=input('nombre'),email=input('email'),dni=input('dni'),ciudad=input('ciudad'),password=input('password'),confirmation=input('confirm-password');
const submit=document.querySelector<HTMLButtonElement>('#register-submit')!;
const error=document.querySelector<HTMLElement>('#register-error')!,status=document.querySelector<HTMLElement>('#register-status')!;
const returnTo=safeReturn(new URLSearchParams(location.search).get('volver'));
if(getSession())location.replace(returnTo);
const loginURL=`/login/?volver=${encodeURIComponent(returnTo)}`;
document.querySelector<HTMLAnchorElement>('#login-link')!.href=loginURL;
document.querySelectorAll<HTMLButtonElement>('[data-toggle-password]').forEach(button=>button.addEventListener('click',()=>{
  const field=input(button.dataset.togglePassword!);const show=field.type==='password';field.type=show?'text':'password';
  button.setAttribute('aria-pressed',String(show));button.setAttribute('aria-label',`${show?'Ocultar':'Mostrar'} ${field===password?'contraseña':'confirmación de contraseña'}`);
}));
function validate(){
  nombre.setCustomValidity(nombre.value.trim()?'':'Ingresá tu nombre completo.');
  ciudad.setCustomValidity(ciudad.value.trim()?'':'Ingresá tu ciudad.');
  email.setCustomValidity(/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.value.trim())?'':'Ingresá un email válido, por ejemplo vos@ejemplo.com.');
  dni.setCustomValidity(/^[0-9]{7,10}$/.test(dni.value)?'':'Ingresá un DNI de entre 7 y 10 números, sin puntos.');
  password.setCustomValidity(password.value.length<4||!password.value.trim()?'La contraseña debe tener al menos 4 caracteres.':new TextEncoder().encode(password.value).length>72?'La contraseña supera el máximo de 72 bytes.':'');
  confirmation.setCustomValidity(confirmation.value===password.value?'':'Las contraseñas no coinciden.');
}
form.addEventListener('input',()=>{validate();error.hidden=true;});
let submitting=false;
const apiBase=(import.meta.env.PUBLIC_API_BASE_URL||'/api').replace(/\/$/,'');
form.addEventListener('submit',async e=>{
  e.preventDefault();if(submitting)return;
  validate();if(!form.reportValidity())return;
  submitting=true;submit.disabled=true;submit.classList.add('loading');form.setAttribute('aria-busy','true');error.hidden=true;
  status.textContent='Creando tu cuenta…';document.querySelector('#register-submit-label')!.textContent='Creando cuenta…';
  const controller=new AbortController();const timeout=setTimeout(()=>controller.abort(),12000);
  const body={nombre:nombre.value.trim(),email:email.value.trim(),dni:dni.value,ciudad:ciudad.value.trim(),password:password.value};
  try{
    const response=await fetch(`${apiBase}/auth/registro`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(body),signal:controller.signal,credentials:'omit'});
    if(!response.ok){
      if(response.status===409)throw new Error('duplicate');
      if(response.status===400)throw new Error('invalid');
      if(response.status===429)throw new Error('rate-limit');
      throw new Error('unavailable');
    }
    let data:unknown;try{data=await response.json();}catch{throw new Error('invalid-response');}
    if(!data||typeof data!=='object'||!('id' in data)||typeof data.id!=='number'||!Number.isFinite(data.id)||data.id<=0)throw new Error('invalid-response');
    password.value='';confirmation.value='';status.textContent='Cuenta creada. Ahora podés ingresar.';
    location.assign(`${loginURL}&registro=ok`);
  }catch(cause){
    const code=cause instanceof Error?cause.message:'';
    const messages:Record<string,string>={duplicate:'Ya existe una cuenta con ese email o DNI. Podés iniciar sesión o revisar tus datos.',invalid:'Revisá los datos del formulario e intentá nuevamente.','rate-limit':'Hubo varios intentos. Esperá un momento antes de volver a crear tu cuenta.','invalid-response':'No pudimos confirmar el registro. Si tu cuenta ya fue creada, podés intentar iniciar sesión.'};
    document.querySelector('#register-error-text')!.textContent=messages[code]||'No pudimos confirmar el registro. Revisá tu conexión e intentá nuevamente. Si ya creaste tu cuenta, podés iniciar sesión.';
    error.hidden=false;error.focus();status.textContent='No se pudo confirmar el registro.';
  }finally{clearTimeout(timeout);submitting=false;submit.disabled=false;submit.classList.remove('loading');form.removeAttribute('aria-busy');document.querySelector('#register-submit-label')!.textContent='Crear mi cuenta';}
});
if(!matchMedia('(prefers-reduced-motion: reduce)').matches){
  gsap.from('.story-content > *',{opacity:0,y:25,duration:.7,stagger:.1,ease:'power3.out',clearProps:'all'});
  gsap.from('.login-form-content > *',{opacity:0,y:20,duration:.6,stagger:.07,ease:'power3.out',clearProps:'all'});
}
