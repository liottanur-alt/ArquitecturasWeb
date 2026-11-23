import { test } from 'node:test';
import assert from 'node:assert/strict';
import { clientRoleId, validateProfile, profileBody, profileLimits } from '../src/scripts/profile-data.ts';

const values = { nombre: ' Ana ', email: ' Ana@example.com ', dni: ' AB-123 ', ciudad: ' Tandil ' };
test('usa el ID real del rol CLIENTE y rechaza rol ausente o respuesta inválida', () => {
  assert.equal(clientRoleId([{ id: 20, name: 'ADMINISTRADOR' }, { id: 47, name: 'CLIENTE' }]), 47);
  for (const roles of [[], null, [{ id: 1, name: 'ADMINISTRADOR' }], [{ id: 0, name: 'CLIENTE' }]]) {
    assert.throws(() => clientRoleId(roles));
  }
});
test('valida campos después de trim y permite DNI no numérico', () => {
  assert.deepEqual(validateProfile(values), {});
  for (const [name, limit] of Object.entries(profileLimits)) {
    assert.ok(validateProfile({ ...values, [name]: ' ' })[name]);
    assert.ok(validateProfile({ ...values, [name]: 'x'.repeat(limit + 1) })[name]);
  }
  assert.ok(validateProfile({ ...values, email: 'invalido@local' }).email);
});
test('el cuerpo conserva el rol recibido y no incluye contraseña ni cambia mayúsculas', () => {
  assert.deepEqual(profileBody({ ...values, password: 'ignorar' }, 93), {
    nombre: 'Ana', email: 'Ana@example.com', dni: 'AB-123', ciudad: 'Tandil', idRol: 93,
  });
  assert.throws(() => profileBody(values, 0));
});
