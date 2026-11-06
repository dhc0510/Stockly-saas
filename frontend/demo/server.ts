import type { IncomingMessage, ServerResponse } from 'node:http'
import { randomUUID } from 'node:crypto'
import type { Company, CompanyInput, Administrator } from '../src/features/companies/api.ts'
import { validate } from '../src/features/companies/validation.ts'

/** Local Vite-only simulator. Never installed on the Spring API or included in the application bundle. */
export function demoMiddleware() {
  let companies: Company[] = [
    ['Ferretería El Sol', 'Andrés Varela Ruiz', '3101765432', 'ACTIVE'],
    ['Papelería Central', 'María Fernanda Soto', '3102884219', 'ACTIVE'],
    ['Comercial Horizonte', 'Carlos Méndez Alfaro', '3105337890', 'SUSPENDED'],
  ].map(([name, fullName, identification, status], index) => ({
    id: randomUUID(), commercialName: name, legalName: `${name} S.A.`,
    identificationType: 'LEGAL', identification, contactEmail: `contacto${index + 1}@example.com`,
    phone: '+506 2222-3344', address: 'San José, Costa Rica',
    status: status as Company['status'], createdAt: `2026-11-0${index + 1}T16:00:00Z`, version: 0,
    administrator: { id: randomUUID(), fullName, username: `admin${index + 1}`, email: `admin${index + 1}@example.com` },
  }))
  const send = (res: ServerResponse, status: number, data?: unknown) => {
    res.statusCode = status
    res.setHeader('Cache-Control', 'no-store')
    res.setHeader('Content-Type', 'application/json; charset=utf-8')
    res.end(status === 204 ? undefined : JSON.stringify(data))
  }
  const failure = (res: ServerResponse, status: number, message: string, fields = {}) => send(res, status, { code: status === 409 ? 'CONFLICT' : 'VALIDATION', message, fieldErrors: fields, requestId: randomUUID() })
  const normalize = (c: CompanyInput): CompanyInput => ({
    commercialName: c.commercialName.trim(), legalName: c.legalName.trim(),
    identificationType: c.identificationType, identification: c.identification.replace(/[\s-]/g, ''),
    contactEmail: c.contactEmail.trim().toLowerCase(), phone: c.phone?.trim() || '', address: c.address?.trim() || '',
  })
  return async (req: IncomingMessage, res: ServerResponse, next: () => void) => {
    const url = new URL(req.url || '/', 'http://localhost')
    if (!url.pathname.startsWith('/api/')) { next(); return }
    if (!/^\/api\/companies(?:\/[^/]+(?:\/(?:suspend|reactivate|delete))?)?$/.test(url.pathname)) { failure(res, 404, 'Ruta no disponible en esta demostración.'); return }
    const [, , , id, action] = url.pathname.split('/')
    try {
      if (req.method === 'GET' && !id) {
        const search = (url.searchParams.get('search') || '').trim().toLowerCase()
        const identification = search.replace(/[\s-]/g, '')
        const status = url.searchParams.get('status')
        const from = url.searchParams.get('registeredFrom') || ''
        const to = url.searchParams.get('registeredTo') || ''
        if (from && to && from > to) { failure(res, 400, 'El inicio no puede ser posterior al fin.'); return }
        const page = Number(url.searchParams.get('page') || 0), size = Number(url.searchParams.get('size') || 20)
        if (!Number.isInteger(page) || page < 0 || !Number.isInteger(size) || size < 1 || size > 100) { failure(res, 400, 'Paginación inválida.'); return }
        const items = companies.filter(c => {
          const date = new Intl.DateTimeFormat('sv-SE', { timeZone: 'America/Costa_Rica' }).format(new Date(c.createdAt))
          return (!search || c.commercialName.toLowerCase().includes(search) || c.legalName.toLowerCase().includes(search) || (/^\d+$/.test(identification) && c.identification.includes(identification))) && (!status || c.status === status) && (!from || date >= from) && (!to || date <= to)
        }).sort((a, b) => b.createdAt.localeCompare(a.createdAt) || b.id.localeCompare(a.id))
        send(res, 200, { items: items.slice(page * size, (page + 1) * size), total: items.length, page, size }); return
      }
      const existing = companies.find(c => c.id === id)
      if (id && !existing) { failure(res, 404, 'La empresa no está disponible.'); return }
      if (req.method === 'GET' && id && !action) { send(res, 200, existing); return }
      if (!['POST', 'PATCH'].includes(req.method || '')) { failure(res, 405, 'Operación no disponible.'); return }
      let raw = ''
      for await (const chunk of req) { raw += chunk.toString(); if (Buffer.byteLength(raw) > 32768) { failure(res, 413, 'Solicitud demasiado grande.'); return } }
      const body = JSON.parse(raw)
      if (existing && body.version !== existing.version && !(action === 'suspend' && existing.status === 'SUSPENDED') && !(action === 'reactivate' && existing.status === 'ACTIVE')) { failure(res, 409, 'La empresa cambió. Recarga la información antes de continuar.'); return }
      if (!id || req.method === 'PATCH') {
        if ((!id && req.method !== 'POST') || (id && action)) { failure(res, 405, 'Operación no disponible.'); return }
        const company = normalize(body.company)
        const admin: Administrator | undefined = !id ? body.administrator : undefined
        const errors = validate(company, admin)
        if (!id && !admin) { failure(res, 400, 'Ingresa un administrador inicial.'); return }
        if (Object.keys(errors).length) { failure(res, 400, 'Revisa los campos indicados.', errors); return }
        if (companies.some(c => c.id !== id && c.identification === company.identification)) { failure(res, 409, 'Esta identificación ya está registrada.', { identification: 'Esta identificación ya está registrada.' }); return }
        if (admin && companies.some(c => c.administrator.username === admin.username.trim().toLowerCase() || c.administrator.email === admin.email.trim().toLowerCase())) { failure(res, 409, 'El usuario o correo del administrador ya está registrado.'); return }
        if (existing) { Object.assign(existing, company, { version: existing.version + 1 }); send(res, 200, existing) }
        else {
          // Destructure explicitly: no password is retained, returned or logged.
          const created: Company = { ...company, id: randomUUID(), status: 'ACTIVE', createdAt: new Date().toISOString(), version: 0,
            administrator: { id: randomUUID(), fullName: admin!.fullName.trim(), username: admin!.username.trim().toLowerCase(), email: admin!.email.trim().toLowerCase() } }
          companies.push(created); send(res, 201, created)
        }
        return
      }
      if (action === 'delete' && req.method === 'POST') {
        if (typeof body.confirmationName !== 'string' || body.confirmationName.trim() !== existing!.commercialName) { failure(res, 400, 'Escribe el nombre exacto de la empresa.'); return }
        companies = companies.filter(c => c.id !== id); send(res, 204); return
      }
      if (['suspend', 'reactivate'].includes(action || '') && req.method === 'POST') {
        const target = action === 'suspend' ? 'SUSPENDED' : 'ACTIVE'
        if (existing!.status !== target) { existing!.status = target; existing!.version++ }
        send(res, 200, existing); return
      }
      failure(res, 405, 'Operación no disponible.')
    } catch { failure(res, 400, 'No se pudo procesar la solicitud de demostración.') }
  }
}
