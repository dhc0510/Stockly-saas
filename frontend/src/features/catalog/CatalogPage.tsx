import { useEffect, useRef, useState, type FormEvent } from "react";
import { normalizeSearch } from "../../lib/search";
import { useSearchParams } from "react-router-dom";
import { ImagePlus, Package, Pencil, Plus, Search, Tag, Tags, Trash2, X } from "lucide-react";
import { request } from "../../lib/http";
import type { SessionUser } from "../auth/api";
import { inventoryApi } from "../inventory/api";
import "./catalog.css";

type Item = {
  id: string;
  variantName: string | null;
  sku: string;
  barcode: string | null;
  unit: string;
  price: number | null;
  active: boolean;
  hasMovements: boolean;
};
type Product = {
  id: string;
  name: string;
  category: string | null;
  description: string | null;
  active: boolean;
  hasImage: boolean;
  items: Item[];
};
type Page = { products: Product[]; categories: string[]; hasMore: boolean };
type Access = ReturnType<typeof catalogAccess>;
type Selection = { product?: Product; item?: Item; category?: string };
type Attribute = { name: string; values: string };

export function catalogAccess(user: SessionUser) {
  const admin = user.role === "COMPANY_ADMIN";
  const level = user.modules.find(module => module.moduleKey === "CATALOG")?.accessLevel;
  return {
    read: admin || (!!level && level !== "NONE"),
    edit: admin || level === "OPERATE" || level === "ADMIN",
    price: admin || user.specialPermissions.includes("CATALOG_PRICE"),
    archive: admin || user.specialPermissions.includes("CATALOG_ARCHIVE"),
  };
}

const api = "/api/catalog";
const currency = new Intl.NumberFormat("es-CR", { style: "currency", currency: "CRC" });
function displayPrice(items: Item[]) {
  const prices = items.flatMap(item => item.price == null ? [] : [item.price]);
  if (!prices.length) return "—";

  const minimum = Math.min(...prices);
  const maximum = Math.max(...prices);
  return <><span className="catalog-amount">{currency.format(minimum)}</span>
    {minimum !== maximum && <> – <span className="catalog-amount">{currency.format(maximum)}</span></>}</>;
}

const errorMessage = (error: unknown) => error instanceof Error ? error.message : "No se pudo completar la operación.";
const write = <T,>(path: string, method: string, body: unknown) =>
  request<T>(api + path, { method, body: JSON.stringify(body) });

function ProductImage({ product, source }: { product?: Product; source?: string }) {
  const image = source ?? (product?.hasImage ? api + "/products/" + product.id + "/image" : "");
  return <span className="catalog-thumbnail">{image
    ? <img src={image} alt="" />
    : <Package size={20} aria-hidden="true" />}</span>;
}

export function CatalogPage({ access }: { access: Access }) {
  const [searchParams] = useSearchParams();
  const branchId = searchParams.get("branchId") ?? "";
  const [page, setPage] = useState<Page>({ products: [], categories: [], hasMore: false });
  const [index, setIndex] = useState(0);
  const [search, setSearch] = useState("");
  const [category, setCategory] = useState("");
  const [active, setActive] = useState(true);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [reload, setReload] = useState(0);
  const [selection, setSelection] = useState<Selection>();
  const [manageCategories, setManageCategories] = useState(false);

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError("");
    const timer = window.setTimeout(() => {
      const query = new URLSearchParams({ search, category, active: String(active), page: String(index) });
      request<Page>(api + "?" + query, { signal: controller.signal })
        .then(result => { if (!controller.signal.aborted) setPage(result); })
        .catch(error => { if (!controller.signal.aborted) setError(errorMessage(error)); })
        .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    }, 180);
    return () => { window.clearTimeout(timer); controller.abort(); };
  }, [search, category, active, index, reload]);

  function done(message: string) {
    setSelection(undefined);
    setNotice(message);
    setReload(value => value + 1);
  }

  function categorySaved(name: string, categoryBeingReplaced?: string) {
    setPage(previous => ({ ...previous, categories: [...new Set([...previous.categories.filter(value => value !== categoryBeingReplaced), ...(name ? [name] : [])])] }));
    setCategory(current => name || (current === categoryBeingReplaced ? "" : current));
    setIndex(0);
    if (name) { setActive(true); setSearch(""); }

    setNotice(!name ? "Categoría eliminada. Sus productos quedaron sin categoría." : categoryBeingReplaced ? "Categoría actualizada." : "Categoría creada. Ya puedes asignarla a un producto.");
    setReload(value => value + 1);
  }

  const term = normalizeSearch(search);
  const rows = page.products.flatMap<{ product: Product; item?: Item }>(product => {
    const group = product.items.some(item => item.variantName !== null);
    const visible = product.items.filter(item => (product.active && item.active) === active);
    if (!group) return visible.map(item => ({ product, item }));
    const exact = term ? visible.filter(item => [item.sku, item.barcode].some(code => normalizeSearch(code) === term)) : [];
    const matches = exact.length || !term || normalizeSearch(product.name).includes(term) ? exact
      : visible.filter(item => [item.sku, item.barcode].some(code => normalizeSearch(code).includes(term)));
    if (matches.length || (!active && product.active)) return (matches.length ? matches : visible).map(item => ({ product, item }));
    return [{ product, item: undefined }];
  });

  if (selection) return <CatalogEditor selection={selection} access={access} categories={page.categories} branchId={branchId}
    cancel={() => setSelection(undefined)} done={done} open={setSelection} />;

  return <section className="catalog-page">
    <div className="catalog-heading"><div><p className="catalog-eyebrow">Inventario / Catálogo</p>
      <h1>Catálogo de productos</h1><p className="subtitle">Consulta y organiza tus productos y variantes.</p></div>
      {access.edit && <div className="catalog-heading-actions">
        <button type="button" aria-haspopup="dialog" onClick={() => { setNotice(""); setManageCategories(true); }}>
          Gestionar categorías
        </button>
        <button className="catalog-primary" onClick={() => { setNotice(""); setSelection({ category }); }}><Plus size={18} /> Nuevo producto</button>
      </div>}
    </div>
    {branchId&&<p className="notice info" role="status">El producto nuevo se asignará a la sucursal desde la que comenzaste.</p>}
    {notice && !manageCategories && <p className="notice" role="status">{notice}</p>}
    {manageCategories && <CategoryManager categories={page.categories} notice={notice} loading={loading}
      close={() => setManageCategories(false)} saved={categorySaved} />}
    <div className="catalog-panel">
      <div className="catalog-filters">
        <label><span>Buscar</span><span className="catalog-search"><Search size={16} aria-hidden="true" /><input type="search" placeholder="Nombre, SKU o código de barras" value={search} onChange={event => { setSearch(event.target.value); setIndex(0); }} /></span></label>
        <label><span>Filtrar por categoría</span><select className={category ? "catalog-category-selected" : undefined} aria-label="Filtrar por categoría" value={category} onChange={event => { setCategory(event.target.value); setIndex(0); }}><option value="">Todas</option>{page.categories.map(value => <option key={value}>{value}</option>)}</select></label>
        <label>Estado<select aria-label="Estado" value={String(active)} onChange={event => { setActive(event.target.value === "true"); setIndex(0); }}><option value="true">Activos</option><option value="false">Archivados</option></select></label>
      </div>
      {loading ? <p className="catalog-state" role="status">Cargando catálogo…</p>
        : error ? <div className="catalog-state" role="alert">{error} <button onClick={() => setReload(value => value + 1)}>Reintentar</button></div>
        : rows.length === 0 ? <p className="catalog-state">No hay productos con estos filtros.</p>
        : <table className="catalog-table"><thead><tr><th>Producto</th><th>SKU</th><th>Categoría</th><th className="catalog-price-column">Precio</th><th className="catalog-status-column">Estado</th></tr></thead>
          <tbody>{rows.map(({ product, item }) => {
            const name = item?.variantName ? product.name + " · " + item.variantName : product.name;
            const code = item?.sku ?? product.items.length + " variantes";
            const price = displayPrice(item ? [item] : product.items.filter(variant => (product.active && variant.active) === active));
            const openProduct = () => { setNotice(""); setSelection({ product, item: item?.variantName ? item : undefined }); };
            return <tr key={item?.id ?? product.id}><td><div className="catalog-product"><ProductImage product={product} />
              <div className="catalog-product-info"><button className="catalog-link" onClick={openProduct}>{name}</button>
                <div className="catalog-mobile-meta">
                  <div>{code}{product.category && <> · {product.category}</>}</div>
                  <div className="catalog-card-price">Precio <strong>{price}</strong></div>
                </div></div></div></td>
              <td className="catalog-wide">{code}</td><td className="catalog-wide">{product.category ?? "—"}</td>
              <td className="catalog-wide catalog-price-column">{price}</td><td className="catalog-status-column"><div className="catalog-row-actions">
                <span className={active ? "catalog-badge active" : "catalog-badge"}>{active ? "Activo" : "Archivado"}</span>
                {(access.edit || access.price) && <button type="button" className="catalog-edit" aria-label={"Editar " + name} onClick={openProduct}>
                  <Pencil size={14} aria-hidden="true" /> Editar
                </button>}
              </div></td></tr>;
          })}</tbody></table>}
      {!loading && !error && (index > 0 || page.hasMore) && <div className="catalog-pagination">
        <button disabled={index === 0} onClick={() => setIndex(value => value - 1)}>Anterior</button>
        <span>Página {index + 1}</span><button disabled={!page.hasMore} onClick={() => setIndex(value => value + 1)}>Siguiente</button></div>}
    </div>
  </section>;
}

function CategoryManager({ categories, close, saved, notice, loading }: {
  categories: string[]; close: () => void; saved: (name: string, previous?: string) => void; notice: string; loading: boolean;
}) {
  const dialog = useRef<HTMLDialogElement>(null);
  const [action, setAction] = useState<{ mode: "create" | "edit" | "delete"; name: string }>();
  const [search, setSearch] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const term = normalizeSearch(search);
  const filtered = categories.filter(name => normalizeSearch(name).includes(term));
  const title = action?.mode === "create" ? "Nueva categoría" : action?.mode === "edit" ? "Editar categoría"
    : action?.mode === "delete" ? "Eliminar categoría" : "Gestionar categorías";

  useEffect(() => {
    const node = dialog.current;
    node?.showModal();
    return () => node?.close();
  }, []);

  function choose(value?: typeof action) {
    setError("");
    setAction(value);
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!action) return;
    const name = String(new FormData(event.currentTarget).get("name") ?? "").trim();
    if (action.mode !== "delete" && !name) { setError("Escribe el nombre de la categoría."); return; }

    setBusy(true);
    setError("");
    try {
      if (action.mode === "delete") {
        await request<void>(api + "/categories?" + new URLSearchParams({ name: action.name }), { method: "DELETE" });
        saved("", action.name);
      } else {
        const editing = action.mode === "edit";
        const result = await write<{ name: string }>("/categories", editing ? "PUT" : "POST",
          editing ? { name, previousName: action.name } : { name });
        saved(result.name, editing ? action.name : undefined);
      }
      setSearch("");
      choose();
    } catch (failure) {
      setError(errorMessage(failure));
    } finally {
      setBusy(false);
    }
  }

  return <dialog ref={dialog} className="catalog-category-dialog" aria-labelledby="catalog-category-title"
    onCancel={event => { event.preventDefault(); if (!busy) close(); }}>
    <div className="catalog-category-header">
      <span className="catalog-category-mark"><Tags size={22} aria-hidden="true" /></span>
      <button type="button" className="catalog-category-icon" aria-label="Cerrar gestión de categorías" disabled={busy} onClick={close}><X size={20} /></button>
      <h2 id="catalog-category-title">{title}</h2>
      {!action && <p>Organiza las categorías que aparecen en tu catálogo.</p>}
    </div>
    {!action ? <div className="catalog-category-body">
      {notice && <p className="catalog-category-feedback" role="status">{notice}</p>}
      <div className="catalog-category-toolbar">
        <span className="catalog-search"><Search size={16} aria-hidden="true" /><input type="search" aria-label="Buscar categorías"
          placeholder="Buscar categorías" value={search} onChange={event => setSearch(event.target.value)} /></span>
        <button type="button" className="catalog-primary" onClick={() => choose({ mode: "create", name: "" })}><Plus size={18} /> Nueva categoría</button>
      </div>
      {(!loading || categories.length > 0) && <p className="catalog-category-count">{filtered.length} {filtered.length === 1 ? "categoría" : "categorías"}</p>}
      {filtered.length ? <ul className="catalog-category-list">{filtered.map(name => <li key={name}>
        <span className="catalog-category-name"><Tag size={17} aria-hidden="true" /> <span>{name}</span></span>
        <div className="catalog-category-row-actions">
          <button type="button" className="catalog-category-icon" aria-label={"Editar categoría " + name}
            onClick={() => choose({ mode: "edit", name })}><Pencil size={17} /></button>
          <button type="button" className="catalog-category-icon catalog-category-delete" aria-label={"Eliminar categoría " + name}
            onClick={() => choose({ mode: "delete", name })}><Trash2 size={17} /></button>
        </div>
      </li>)}</ul> : <p className="catalog-category-empty">{loading && !categories.length ? "Cargando categorías…" : term ? "No hay categorías que coincidan con tu búsqueda." : "Aún no hay categorías."}</p>}
    </div> : <form className="catalog-category-body catalog-category-fields" onSubmit={submit}>
      {action.mode === "delete"
        ? <p>¿Eliminar <strong>{action.name}</strong>? Sus productos quedarán en <strong>Sin categoría</strong>.</p>
        : <label>Nombre de la categoría<input key={action.name} name="name" defaultValue={action.name} required maxLength={120} autoFocus /></label>}
      {error && <p className="notice error" role="alert">{error}</p>}
      <div className="catalog-category-footer">
        <button type="button" onClick={() => choose()} disabled={busy}>Cancelar</button>
        <button className={action.mode === "delete" ? "catalog-category-danger" : "catalog-primary"} disabled={busy}>
          {busy ? "Procesando…" : action.mode === "delete" ? "Eliminar categoría" : "Guardar categoría"}
        </button>
      </div>
    </form>}
  </dialog>;
}


function CatalogEditor({ selection, access, categories, branchId, cancel, done, open }: {
  selection: Selection; access: Access; categories: string[]; branchId:string;
  cancel: () => void; done: (message: string) => void; open: (value: Selection) => void;
}) {
  const key = selection.item?.id ?? selection.product?.id ?? "new";
  return <ProductForm key={key} {...{ selection, access, categories, branchId, cancel, done, open }} />;
}

function ProductForm({ selection: { product, item, category }, access, categories, branchId, cancel, done, open }: {
  selection: Selection; access: Access; categories: string[]; branchId:string;
  cancel: () => void; done: (message: string) => void; open: (value: Selection) => void;
}) {
  const simple = product?.items.find(value => value.variantName === null);
  const current = item ?? simple;
  const isGroup = !!product && !simple && !item;
  const imageInput = useRef<HTMLInputElement>(null);
  const [image, setImage] = useState<string>();
  const [imageLoading, setImageLoading] = useState(false);
  const [attributes, setAttributes] = useState<Attribute[]>([{ name: "", values: "" }]);
  const [configure, setConfigure] = useState(false);
  const [combinations, setCombinations] = useState<string[]>([]);
  const [selected, setSelected] = useState<string[]>([]);
  const [keep, setKeep] = useState("");
  const [confirm, setConfirm] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const title = item ? item.variantName : product?.name ?? "Nuevo producto";

  function setAttribute(index: number, patch: Partial<Attribute>) {
    setAttributes(values => values.map((value, position) => position === index ? { ...value, ...patch } : value));
    clearCombinations();
  }

  function clearCombinations() {
    setCombinations([]);
    setSelected([]);
    setKeep("");
  }

  function preview() {
    setError("");
    let result = [""];
    const names = new Set<string>();
    for (const attribute of attributes) {
      const name = attribute.name.trim();
      const values = [...new Set(attribute.values.split(",").map(value => value.trim()).filter(Boolean))];
      if (!name || !values.length || names.has(name.toLocaleLowerCase())) {
        setError("Completa atributos distintos con al menos un valor.");
        return;
      }
      names.add(name.toLocaleLowerCase());
      if (result.length * values.length > 100) {
        setError("Reduce los valores para previsualizar hasta 100 combinaciones.");
        return;
      }
      result = result.flatMap(prefix => values.map(value => (prefix ? prefix + " / " : "") + name + ": " + value));
    }
    setCombinations(result);
    setSelected([]);
    setKeep("");
  }

  async function chooseImage(file?: File) {
    if (!file) return;
    if (!["image/jpeg", "image/png", "image/webp"].includes(file.type) || file.size > 2 * 1024 * 1024) {
      setError("Selecciona una imagen JPG, PNG o WebP de hasta 2 MB.");
      return;
    }
    setError("");
    setImageLoading(true);
    const reader = new FileReader();
    reader.onload = () => { setImage(String(reader.result)); setImageLoading(false); };
    reader.onerror = () => { setError("No se pudo leer la imagen."); setImageLoading(false); };
    reader.readAsDataURL(file);
  }

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const value = (key: string) => String(form.get(key) ?? "");
    const converting = configure && !!simple;
    if (configure && (!selected.length || (converting && !selected.includes(keep)))) {
      setError("Selecciona las combinaciones y, al convertir, elige cuál conserva el artículo original.");
      return;
    }
    const price = value("price") === "" ? null : Number(value("price"));
    const body = { sku: value("sku"), barcode: value("barcode"), unit: value("unit") || current?.unit || "unidad",
      price, setPrice: access.price && !converting };
    setBusy(true);
    setError("");
    let productCreated=false;
    try {
      if (!access.edit && current && access.price) {
        await write("/items/" + current.id + "/price", "PUT", { price });
      } else if (item) {
        await write("/items/" + item.id, "PUT", body);
      } else {
        const saved=await write<Product>("/products" + (product ? "/" + product.id : ""), product ? "PUT" : "POST", {
          name: value("name"), category: value("category"), description: value("description"),
          item: isGroup ? null : body, variants: configure ? selected : [], keepVariant: keep,
          image: image ? image.split(",")[1] : null, removeImage: image === "",
        });
        productCreated=!product;
        if(branchId&&!product){
          try { for(const savedItem of saved.items.filter(value=>value.active)) await inventoryApi.assignItem(branchId,{itemId:savedItem.id,minimumQuantity:null,lowThreshold:2,recoveryMargin:1,exhaustedBasis:"AVAILABLE"}); }
          catch { done("Producto creado; la asignación a sucursal quedó pendiente. Asígnalo desde Sucursales."); return; }
        }
      }
      done(branchId&&!product?"Producto creado y asignado a la sucursal.":"Cambios guardados.");
    } catch (failure) { if(productCreated)done("Producto creado; revisa su asignación a sucursal.");else setError(errorMessage(failure)); }
    finally { setBusy(false); }
  }

  async function changeStatus() {
    if (!product) return;
    setBusy(true);
    setError("");
    const record = item ?? product;
    try {
      await write("/" + (item ? "items" : "products") + "/" + record.id + "/status", "PUT", { active: !record.active });
      done(record.active ? "Artículo archivado." : "Artículo reactivado.");
    } catch (failure) { setError(errorMessage(failure)); }
    finally { setBusy(false); }
  }

  const canSave = access.edit || (access.price && !!current);
  return <section className="catalog-page">
    <div className="catalog-heading"><div><p className="catalog-eyebrow">Inventario / Catálogo</p>
      <h1>{title}</h1>{item && <p className="subtitle">{product?.name}</p>}</div><button onClick={item ? () => open({ product }) : cancel}>Volver</button></div>
    <form className="catalog-panel catalog-form" onSubmit={save}>
      {!item && <><label>Nombre<input name="name" maxLength={160} required defaultValue={product?.name} disabled={!access.edit} /></label>
        <label>Categoría<select name="category" aria-label="Categoría" defaultValue={(product ? product.category : category) ?? ""} disabled={!access.edit}>
          <option value="">Sin categoría</option>
          {categories.map(name => <option key={name} value={name}>{name}</option>)}
        </select></label>
        <label>Descripción<textarea name="description" maxLength={2000} defaultValue={product?.description ?? ""} disabled={!access.edit} /></label>
        <fieldset><legend>Imagen del producto · Opcional</legend><div className="catalog-image">
          {(image || (image === undefined && product?.hasImage)) && <ProductImage product={product} source={image} />}
          {access.edit && <><input ref={imageInput} type="file" hidden accept="image/jpeg,image/png,image/webp" onChange={event => { void chooseImage(event.target.files?.[0]); event.target.value = ""; }} />
            <button type="button" className="catalog-image-picker" disabled={busy || imageLoading} onClick={() => imageInput.current?.click()}>
              <ImagePlus size={18} aria-hidden="true" />
              {imageLoading ? "Cargando imagen…" : image || (image === undefined && product?.hasImage) ? "Cambiar imagen" : "Seleccionar imagen"}
            </button>
            {(image || (image === undefined && product?.hasImage)) && <button type="button" disabled={imageLoading} onClick={() => setImage("")}>Quitar imagen</button>}</>}
        </div><small>JPG, PNG o WebP · Hasta 2 MB</small></fieldset></>}
      {!isGroup && <div className="catalog-fields">
        <label>SKU<input name="sku" maxLength={80} defaultValue={current?.sku} placeholder="Automático si se deja vacío" disabled={!access.edit || (configure && !!simple)} /></label>
        <label>Código de barras<input name="barcode" maxLength={80} defaultValue={current?.barcode ?? ""} disabled={!access.edit || (configure && !!simple)} /></label>
        <label>Unidad base<select name="unit" defaultValue={current?.unit ?? "unidad"} disabled={!access.edit || current?.hasMovements || (configure && !!simple)}>{["unidad", "kg", "litro", "metro"].map(unit => <option key={unit}>{unit}</option>)}</select></label>
        <label>Precio de venta (₡)<input name="price" type="number" step=".01" min="0" max="9999999999999.99" defaultValue={current?.price ?? ""} disabled={!access.price || (configure && !!simple)} /></label>
      </div>}
      {isGroup && <fieldset><legend>Variantes</legend><div className="catalog-variants">{product.items.map(variant =>
        <button key={variant.id} type="button" onClick={() => open({ product, item: variant })}>{variant.variantName} · {variant.sku}{variant.price != null && " · " + currency.format(variant.price)}{!variant.active && " · Archivada"}</button>)}</div></fieldset>}
      {!item && access.edit && <fieldset><legend>{isGroup ? "Añadir variantes" : "Variantes"}</legend>
        <label className="catalog-check"><input type="checkbox" checked={configure} onChange={event => setConfigure(event.target.checked)} />{isGroup ? "Añadir combinaciones" : simple ? "Convertir a producto con variantes" : "Crear con variantes"}</label>
        {configure && <>
          {attributes.map((attribute, index) => <div className="catalog-fields catalog-attribute" key={index}>
            <label>Atributo {index + 1}<input value={attribute.name} onChange={event => setAttribute(index, { name: event.target.value })} placeholder="Ej. Color" /></label>
            <label>Valores separados por coma<input value={attribute.values} onChange={event => setAttribute(index, { values: event.target.value })} placeholder="Ej. Rojo, Azul" /></label>
            <button type="button" className="catalog-remove-attribute" aria-label={"Quitar atributo " + (index + 1)}
              onClick={() => { setAttributes(values => values.filter((_, position) => position !== index)); clearCombinations(); }}>
              <X size={18} aria-hidden="true" />
            </button>
          </div>)}
          <div className="catalog-actions"><button type="button" onClick={() => { setAttributes(values => [...values, { name: "", values: "" }]); clearCombinations(); }}>Añadir atributo</button>
            <button type="button" onClick={preview} disabled={!attributes.length}>Ver combinaciones</button></div>
          {combinations.map(combination => <label className="catalog-check" key={combination}><input type="checkbox" checked={selected.includes(combination)}
            onChange={event => setSelected(values => event.target.checked ? [...values, combination] : values.filter(value => value !== combination))} />{combination}</label>)}
          {simple && selected.length > 0 && <label>Variante que conserva el SKU, existencias e historial<select value={keep} onChange={event => setKeep(event.target.value)}><option value="">Selecciona una variante</option>{selected.map(value => <option key={value}>{value}</option>)}</select></label>}
        </>}
      </fieldset>}
      {error && <p className="notice error" role="alert">{error}</p>}
      {confirm && <div className="notice" role="alert">
        <p>{(item ?? product)?.active ? "El artículo quedará fuera de la búsqueda habitual y de nuevas ventas. Se conservará su historial." : "El artículo volverá a estar disponible cuando su ficha esté activa."}</p>
        <div className="catalog-actions"><button type="button" onClick={() => setConfirm(false)} disabled={busy}>Cancelar</button><button type="button" className="catalog-primary" onClick={() => void changeStatus()} disabled={busy}>Confirmar</button></div>
      </div>}
      <div className="catalog-actions">
        {product && access.archive && !confirm && <button type="button" onClick={() => setConfirm(true)} disabled={busy}>{(item ?? product).active ? "Archivar" : "Reactivar"}</button>}
        {canSave && <button type="submit" className="catalog-primary" disabled={busy || imageLoading}>{busy ? "Guardando…" : "Guardar cambios"}</button>}
      </div>
    </form>
  </section>;
}
