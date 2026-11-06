import { test, expect } from '@playwright/test'

test('la demostración funciona sin backend, conserva cambios al recargar y no guarda contraseñas', async ({ page, request }, testInfo) => {
  const name = `Negocio de prueba ${testInfo.project.name}`
  const suffix = testInfo.project.name === 'desktop' ? '7' : '8'
  const requests: string[] = []
  page.on('request', req => requests.push(req.url()))
  await page.goto('/companies')
  await expect(page.getByText('Demostración local', { exact: true })).toBeVisible()
  await expect(page.getByText('Ferretería El Sol', { exact: true })).toBeVisible()
  await page.getByRole('link', { name: 'Registrar empresa', exact: true }).click()
  for (const [label, value] of [['Nombre comercial *', name], ['Nombre o razón social *', `${name} SA`], ['Número de identificación *', `310199999${suffix}`], ['Correo de contacto *', 'contacto@example.com'], ['Nombre completo *', 'Admin de prueba'], ['Nombre de usuario *', `demo${suffix}`], ['Correo del administrador *', `demo${suffix}@example.com`], ['Contraseña temporal *', 'contraseña ficticia temporal']]) await page.getByLabel(label, { exact: true }).fill(value)
  await page.getByLabel('Tipo de identificación *').selectOption('LEGAL')
  await page.getByRole('button', { name: 'Registrar empresa', exact: true }).click()
  await expect(page.getByRole('heading', { name, exact: true })).toBeVisible()
  const detailPath = new URL(page.url()).pathname.replace('/companies', '/api/companies')
  const response = await request.get(detailPath)
  expect(response.ok()).toBe(true)
  expect(await response.text()).not.toMatch(/contraseña ficticia|temporaryPassword|passwordHash/)
  await page.reload()
  await expect(page.getByRole('heading', { name, exact: true })).toBeVisible()
  await page.getByRole('button', { name: 'Suspender', exact: true }).click()
  await page.getByRole('button', { name: 'Suspender empresa', exact: true }).click()
  await expect(page.getByRole('button', { name: 'Reactivar', exact: true })).toBeVisible()
  await page.getByRole('button', { name: 'Reactivar', exact: true }).click()
  await page.getByRole('button', { name: 'Reactivar empresa', exact: true }).click()
  await page.getByRole('button', { name: 'Eliminar empresa', exact: true }).click()
  await page.getByRole('textbox', { name: /Escribe/ }).fill(name)
  await page.getByRole('button', { name: 'Eliminar definitivamente' }).click()
  await expect(page.getByText('Empresa eliminada definitivamente.')).toBeVisible()
  expect(requests.every(url => !url.includes(':8080'))).toBe(true)
})
