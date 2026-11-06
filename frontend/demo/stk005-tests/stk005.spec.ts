import { test, expect } from '@playwright/test'

test('ingresa como administrador y recorre roles, usuarios, sesión y auditoría', async ({ page }, testInfo) => {
  const suffix = testInfo.project.name === 'desktop' ? '71' : '72'
  const navigateTo = async (name: string) => {
    const item = page.getByRole('link', { name })
    if (testInfo.project.name === 'mobile') {
      await expect(item).toBeHidden()
      await page.getByRole('button', { name: 'Abrir menú' }).click()
      await expect(item).toBeVisible()
    }
    await item.click()
    if (testInfo.project.name === 'mobile') await expect(page.getByRole('button', { name: 'Abrir menú' })).toHaveAttribute('aria-expanded', 'false')
  }
  await page.goto('/')
  await expect(page.getByText('Simulación STK-005', { exact: true })).toBeVisible()
  await page.getByLabel('Usuario o correo').fill('admin-demo')
  await page.getByLabel('Contraseña').fill('Stockly-Demo-2026!')
  await page.getByRole('button', { name: 'Ingresar' }).click()
  await expect(page.getByRole('heading', { name: 'Usuarios' })).toBeVisible()
  await expect(page.getByText('Simulación STK-005', { exact: true })).toBeVisible()

  await navigateTo('Roles y permisos')
  await page.getByRole('button', { name: 'Crear rol' }).click()
  await page.getByLabel('Nombre del rol').fill(`Rol de prueba ${suffix}`)
  await page.getByRole('button', { name: 'Guardar rol' }).click()
  await expect(page.getByRole('heading', { name: `Rol de prueba ${suffix}` })).toBeVisible()

  await navigateTo('Usuarios')
  await page.getByRole('button', { name: 'Crear trabajador' }).click()
  await page.getByLabel('Nombre completo').fill(`Trabajador de prueba ${suffix}`)
  await page.getByLabel('Nombre de usuario').fill(`trabajador${suffix}`)
  await page.getByLabel('Correo electrónico').fill(`trabajador${suffix}@stockly-demo.local`)
  await page.getByRole('combobox', { name: 'Rol de trabajador' }).selectOption({ label: `Rol de prueba ${suffix}` })
  await page.getByRole('button', { name: 'Crear trabajador' }).last().click()
  await expect(page.getByText('Contraseña temporal de un solo uso')).toBeVisible()
  await expect(page.getByText(`Trabajador de prueba ${suffix}`, { exact: true })).toBeVisible()

  await navigateTo('Seguridad')
  await expect(page.getByRole('heading', { name: 'Sesiones' })).toBeVisible()
  await navigateTo('Actividad')
  await expect(page.getByRole('heading', { name: 'Actividad' })).toBeVisible()
  const activityList = page.locator(testInfo.project.name === 'mobile' ? '.audit-mobile-list' : '.audit-desktop')
  await expect(activityList.getByText('Trabajador creado', { exact: true }).first()).toBeVisible()
})
