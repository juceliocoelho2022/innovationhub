import { useEffect, useState } from 'react'
import { getDashboardSummary, getProjects } from './api'

const navItems = [
  ['⌂', 'Dashboard'],
  ['▣', 'Projetos'],
  ['▤', 'Atividades'],
  ['♙', 'Equipes'],
  ['◫', 'Orçamentos'],
  ['◉', 'Indicadores'],
  ['△', 'Riscos'],
  ['▥', 'Relatórios'],
  ['◇', 'Auditoria'],
  ['⚙', 'Configurações']
]

const emptySummary = {
  totalProjects: 0,
  activeProjects: 0,
  inProgress: 0,
  atRisk: 0,
  completed: 0,
  totalBudget: 0
}

const money = new Intl.NumberFormat('pt-BR', {
  style: 'currency',
  currency: 'BRL',
  maximumFractionDigits: 0
})

function statusLabel(status) {
  const map = {
    DRAFT: 'Rascunho',
    UNDER_ANALYSIS: 'Em análise',
    APPROVED: 'Aprovado',
    IN_PROGRESS: 'Em andamento',
    AT_RISK: 'Em risco',
    COMPLETED: 'Concluído',
    CANCELLED: 'Cancelado'
  }
  return map[status] ?? status
}

export default function App() {
  const [summary, setSummary] = useState(emptySummary)
  const [projects, setProjects] = useState([])
  const [error, setError] = useState('')

  useEffect(() => {
    Promise.all([getDashboardSummary(), getProjects()])
      .then(([dashboard, page]) => {
        setSummary(dashboard)
        setProjects(page.content ?? [])
      })
      .catch((err) => setError(err.message))
  }, [])

  const cards = [
    ['Projetos ativos', summary.activeProjects, '▣'],
    ['Em andamento', summary.inProgress, '▷'],
    ['Em risco', summary.atRisk, '△'],
    ['Orçamento total', money.format(summary.totalBudget ?? 0), '◉'],
    ['Concluídos', summary.completed, '✓'],
    ['Portfólio total', summary.totalProjects, '◎']
  ]

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="brand">
          <div className="brand-mark">IH</div>
          <div>
            <strong>InnovationHub</strong>
            <small>Ideias hoje. Soluções amanhã.</small>
          </div>
        </div>

        <nav>
          {navItems.map(([icon, label], index) => (
            <button className={index === 0 ? 'nav-item active' : 'nav-item'} key={label}>
              <span>{icon}</span>{label}
            </button>
          ))}
        </nav>

        <div className="sidebar-note">
          <span className="bulb">◌</span>
          <strong>Inovar é construir um futuro melhor, juntos.</strong>
          <small>InnovationHub v0.1.0</small>
        </div>
      </aside>

      <main>
        <header className="topbar">
          <div className="search">⌕ <span>Buscar projetos, pessoas, documentos...</span></div>
          <div className="profile">
            <span className="notification">♢<b>3</b></span>
            <div className="avatar">JC</div>
            <div><strong>Jucelio</strong><small>Gerente de Inovação</small></div>
          </div>
        </header>

        <section className="content">
          <div className="title-row">
            <div>
              <h1>Dashboard</h1>
              <p>Visão geral do portfólio de inovação</p>
            </div>
            <span className="quote">“Inovação transforma possibilidades em realidade.”</span>
          </div>

          {error && <div className="alert">{error} Suba o backend para visualizar os dados reais.</div>}

          <div className="kpis">
            {cards.map(([label, value, icon]) => (
              <article className="card kpi" key={label}>
                <div className="kpi-icon">{icon}</div>
                <div>
                  <span>{label}</span>
                  <strong>{value}</strong>
                  <small>Visão consolidada do portfólio</small>
                </div>
              </article>
            ))}
          </div>

          <div className="grid">
            <article className="card portfolio">
              <div className="card-head">
                <h2>Portfólio de projetos por área de inovação</h2>
                <button>Ver detalhes →</button>
              </div>
              <div className="bars">
                {[82, 60, 46, 38, 52, 28].map((height, i) => (
                  <div className="bar-item" key={i}>
                    <span style={{ height: `${height}%` }} />
                    <small>{['IA', 'Indústria 4.0', 'Sustentabilidade', 'Novos Produtos', 'Transformação', 'Outros'][i]}</small>
                  </div>
                ))}
              </div>
            </article>

            <article className="card status-card">
              <div className="card-head"><h2>Status dos projetos</h2></div>
              <div className="donut-wrap">
                <div className="donut"><div><strong>{summary.totalProjects}</strong><span>projetos</span></div></div>
                <ul>
                  <li><i className="green"/>Em andamento <b>{summary.inProgress}</b></li>
                  <li><i className="orange"/>Em risco <b>{summary.atRisk}</b></li>
                  <li><i className="blue"/>Concluído <b>{summary.completed}</b></li>
                </ul>
              </div>
            </article>
          </div>

          <article className="card projects">
            <div className="card-head">
              <h2>Projetos recentes</h2>
              <button>Ver todos →</button>
            </div>
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Projeto</th>
                    <th>Gerente</th>
                    <th>Status</th>
                    <th>Prazo</th>
                    <th>Orçamento</th>
                  </tr>
                </thead>
                <tbody>
                  {projects.length === 0 ? (
                    <tr><td colSpan="5" className="empty">Nenhum projeto carregado.</td></tr>
                  ) : projects.map(project => (
                    <tr key={project.id ?? project.code}>
                      <td><strong>{project.name}</strong><small>{project.code}</small></td>
                      <td>{project.managerName}</td>
                      <td><span className={`pill ${project.status.toLowerCase()}`}>{statusLabel(project.status)}</span></td>
                      <td>{project.endDate}</td>
                      <td>{money.format(project.budget)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </article>
        </section>
      </main>
    </div>
  )
}
