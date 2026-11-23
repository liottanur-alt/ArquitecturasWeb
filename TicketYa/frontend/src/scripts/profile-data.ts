export const profileLimits = { nombre: 100, email: 254, dni: 20, ciudad: 100 } as const;
export type ProfileField = keyof typeof profileLimits;
export type ProfileValues = Record<ProfileField, string>;
export type Profile = ProfileValues & { id: number; idRol: number };

export function validateProfile(values: ProfileValues) {
  const errors: Partial<Record<ProfileField, string>> = {};
  for (const name of Object.keys(profileLimits) as ProfileField[]) {
    const value = values[name].trim();
    if (!value) errors[name] = 'Completá este campo.';
    else if (value.length > profileLimits[name]) errors[name] = `Usá hasta ${profileLimits[name]} caracteres.`;
  }
  if (!errors.email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(values.email.trim())) {
    errors.email = 'Ingresá un email válido, por ejemplo vos@ejemplo.com.';
  }
  return errors;
}

export function profileBody(values: ProfileValues, idRol: number) {
  if (!Number.isSafeInteger(idRol) || idRol <= 0) throw new Error('El rol recibido no es válido.');
  if (Object.keys(validateProfile(values)).length) throw new Error('Revisá los datos del perfil.');
  return { nombre: values.nombre.trim(), email: values.email.trim(), dni: values.dni.trim(), ciudad: values.ciudad.trim(), idRol };
}

export function validProfile(data: unknown): data is Profile {
  if (!data || typeof data !== 'object') return false;
  const value = data as Profile;
  return Number.isSafeInteger(value.id) && value.id > 0 && Number.isSafeInteger(value.idRol) && value.idRol > 0
    && Object.keys(profileLimits).every(name => typeof value[name as ProfileField] === 'string');
}

export function clientRoleId(data: unknown): number {
  if (!Array.isArray(data) || !data.every(role => role && Number.isSafeInteger(role.id) && role.id > 0 && typeof role.name === 'string')) {
    throw new Error('El servidor devolvió una lista de roles inválida.');
  }
  const clients = data.filter(role => role.name === 'CLIENTE');
  if (clients.length !== 1) throw new Error('El rol CLIENTE no está disponible. No se puede crear el perfil.');
  return clients[0].id;
}
