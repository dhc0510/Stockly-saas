export class ApiError extends Error {
  constructor(
    public status: number,
    public code: string,
    message: string,
    public fieldErrors: Record<string, string> = {},
  ) {
    super(message);
  }
}

type SessionBridge = Window & {
  stocklySession?: { csrfToken: string; csrfHeaderName?: string };
};

/** STK-005 supplies the authenticated session and CSRF token, never a hard-coded role. */
export async function request<T>(
  path: string,
  options: RequestInit = {},
): Promise<T> {
  const headers = new Headers(options.headers);
  if (options.body) headers.set("Content-Type", "application/json");
  if (options.method && !["GET", "HEAD", "OPTIONS"].includes(options.method)) {
    const session = (window as SessionBridge).stocklySession;
    if (session?.csrfToken)
      headers.set(session.csrfHeaderName || "X-CSRF-TOKEN", session.csrfToken);
  }
  let response: Response;
  try {
    response = await fetch(path, {
      ...options,
      headers,
      credentials: "same-origin",
      cache: "no-store",
    });
  } catch (error) {
    if (error instanceof DOMException && error.name === "AbortError")
      throw error;
    throw new ApiError(
      0,
      "NETWORK",
      "No se pudo confirmar la operación. Revisa tu conexión y consulta el estado antes de reenviar.",
    );
  }
  if (!response.ok) {
    const body = await response.json().catch(() => ({}));
    throw new ApiError(
      response.status,
      body.code || "REQUEST_FAILED",
      body.message || "No se pudo completar la solicitud.",
      body.fieldErrors || {},
    );
  }
  if (response.status === 204) return undefined as T;
  return response.json();
}
