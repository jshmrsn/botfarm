export interface AdminRequest {
  serverAdminSecret: string
}

export function buildAdminRequestForSecret(serverAdminSecret: string): AdminRequest {
  return {
    serverAdminSecret: serverAdminSecret
  }
}
