export async function getDashboardSummary() {
  const response = await fetch('/api/v1/dashboard/summary')
  if (!response.ok) throw new Error('Não foi possível carregar o dashboard.')
  return response.json()
}

export async function getProjects() {
  const response = await fetch('/api/v1/projects?page=0&size=10&sort=createdAt,desc')
  if (!response.ok) throw new Error('Não foi possível carregar os projetos.')
  return response.json()
}
