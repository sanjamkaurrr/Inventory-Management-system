import { BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer, PieChart, Pie, Cell } from 'recharts'

const COLORS = ['#6366f1', '#22d3ee', '#10b981', '#f59e0b', '#f43f5e', '#8b5cf6']

export default function Dashboard({ data }) {
  if (!data) return <div className="loading-spinner"><div className="spinner" /></div>

  const { warehouses, orderStats, systemStats, tierUsage, recentOrders, inventorySummary } = data

  // Compute totals
  let totalAvailable = 0, totalReserved = 0, totalDefective = 0
  if (warehouses) {
    Object.values(warehouses).forEach(wh => {
      totalAvailable += wh.totalAvailable || 0
      totalReserved += wh.totalReserved || 0
      totalDefective += wh.totalDefective || 0
    })
  }

  const totalOrders = orderStats?.totalOrders || 0
  const successRate = orderStats?.successRate || 100
  const pendingOrders = (orderStats?.statusCounts?.RESERVED || 0) + (orderStats?.statusCounts?.PENDING || 0)

  // Chart data
  const warehouseChartData = warehouses ? Object.entries(warehouses).map(([id, wh]) => ({
    name: id.replace('_', ' '),
    available: wh.totalAvailable,
    reserved: wh.totalReserved,
    defective: wh.totalDefective
  })) : []

  const statusData = orderStats?.statusCounts ?
    Object.entries(orderStats.statusCounts).filter(([, v]) => v > 0).map(([name, value]) => ({ name, value })) : []

  return (
    <div>
      <div className="page-header">
        <div>
          <h2>Dashboard</h2>
          <p className="subtitle">Real-time warehouse overview</p>
        </div>
        <div className="header-actions">
          <div className="live-indicator">
            <span className="live-dot" />
            Live
          </div>
        </div>
      </div>

      {/* Stats Cards */}
      <div className="stats-grid">
        <div className="stat-card indigo">
          <div className="stat-label">📦 Total Available Stock</div>
          <div className="stat-value">{totalAvailable.toLocaleString()}</div>
          <div className="stat-change up">↗ Across all warehouses</div>
        </div>
        <div className="stat-card emerald">
          <div className="stat-label">🧾 Total Orders</div>
          <div className="stat-value">{totalOrders.toLocaleString()}</div>
          <div className="stat-change up">↗ {systemStats?.totalOrdersProcessed || 0} processed</div>
        </div>
        <div className="stat-card amber">
          <div className="stat-label">⏳ Pending / Reserved</div>
          <div className="stat-value">{pendingOrders}</div>
          <div className="stat-change">{totalReserved} items reserved</div>
        </div>
        <div className="stat-card rose">
          <div className="stat-label">✅ Success Rate</div>
          <div className="stat-value">{successRate}%</div>
          <div className={`stat-change ${successRate >= 90 ? 'up' : 'down'}`}>
            {successRate >= 90 ? '↗' : '↘'} {systemStats?.totalOrdersFailed || 0} failed
          </div>
        </div>
      </div>

      {/* Warehouse Cards */}
      <div className="card" style={{ marginBottom: 20 }}>
        <div className="card-header">
          <h3>🏭 Warehouse Overview</h3>
          <span className="card-badge">{warehouses ? Object.keys(warehouses).length : 0} active</span>
        </div>
        <div className="warehouse-grid">
          {warehouses && Object.entries(warehouses).map(([id, wh]) => {
            const utilPct = wh.utilizationPercent || 0
            const fillClass = utilPct > 80 ? 'high' : utilPct > 50 ? 'medium' : ''
            return (
              <div className="warehouse-card" key={id}>
                <div className="wh-header">
                  <div>
                    <div className="wh-name">{wh.name}</div>
                    <div className="wh-id">{id}</div>
                  </div>
                  <div className="wh-status" />
                </div>
                <div className="utilization-bar">
                  <div className={`utilization-fill ${fillClass}`}
                       style={{ width: `${Math.min(utilPct, 100)}%` }} />
                </div>
                <div style={{ fontSize: 12, color: 'var(--text-muted)', textAlign: 'right' }}>
                  {utilPct}% capacity · {wh.skuCount} SKUs
                </div>
                <div className="wh-stats">
                  <div className="wh-stat">
                    <div className="label">Available</div>
                    <div className="value available">{(wh.totalAvailable || 0).toLocaleString()}</div>
                  </div>
                  <div className="wh-stat">
                    <div className="label">Reserved</div>
                    <div className="value reserved">{(wh.totalReserved || 0).toLocaleString()}</div>
                  </div>
                  <div className="wh-stat">
                    <div className="label">Defective</div>
                    <div className="value defective">{(wh.totalDefective || 0).toLocaleString()}</div>
                  </div>
                </div>
              </div>
            )
          })}
        </div>
      </div>

      {/* Charts Row */}
      <div className="dashboard-grid">
        <div className="card">
          <div className="card-header">
            <h3>📊 Stock by Warehouse</h3>
          </div>
          <div className="chart-container">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={warehouseChartData} barGap={4}>
                <XAxis dataKey="name" tick={{ fill: '#94a3b8', fontSize: 12 }} axisLine={false} tickLine={false} />
                <YAxis tick={{ fill: '#94a3b8', fontSize: 12 }} axisLine={false} tickLine={false} />
                <Tooltip contentStyle={{ background: '#1e293b', border: '1px solid rgba(255,255,255,0.1)',
                  borderRadius: 8, color: '#f1f5f9', fontSize: 13 }} />
                <Bar dataKey="available" fill="#10b981" radius={[4, 4, 0, 0]} name="Available" />
                <Bar dataKey="reserved" fill="#f59e0b" radius={[4, 4, 0, 0]} name="Reserved" />
                <Bar dataKey="defective" fill="#f43f5e" radius={[4, 4, 0, 0]} name="Defective" />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </div>

        <div className="card">
          <div className="card-header">
            <h3>🎯 Order Status</h3>
          </div>
          {statusData.length > 0 ? (
            <div className="chart-container" style={{ display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              <ResponsiveContainer width="100%" height="100%">
                <PieChart>
                  <Pie data={statusData} dataKey="value" nameKey="name" cx="50%" cy="50%"
                       innerRadius={60} outerRadius={100} paddingAngle={4} strokeWidth={0}>
                    {statusData.map((_, i) => (
                      <Cell key={i} fill={COLORS[i % COLORS.length]} />
                    ))}
                  </Pie>
                  <Tooltip contentStyle={{ background: '#1e293b', border: '1px solid rgba(255,255,255,0.1)',
                    borderRadius: 8, color: '#f1f5f9', fontSize: 13 }} />
                </PieChart>
              </ResponsiveContainer>
            </div>
          ) : (
            <div className="empty-state">
              <div className="icon">📭</div>
              <p>No orders yet. Place your first order!</p>
            </div>
          )}
          {statusData.length > 0 && (
            <div style={{ display: 'flex', gap: 16, justifyContent: 'center', flexWrap: 'wrap', marginTop: 8 }}>
              {statusData.map((d, i) => (
                <div key={d.name} style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 12 }}>
                  <div style={{ width: 10, height: 10, borderRadius: 3, background: COLORS[i % COLORS.length] }} />
                  <span style={{ color: '#94a3b8' }}>{d.name}: {d.value}</span>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>

      {/* Tier Usage + Recent Orders */}
      <div className="dashboard-grid">
        <div className="card">
          <div className="card-header">
            <h3>⚡ Tier Usage</h3>
            <span className="card-badge">{tierUsage?.tier || 'FREE'}</span>
          </div>
          <div className="tier-usage-grid">
            <TierBar label="Daily Requests" used={tierUsage?.dailyUsed}
                     max={typeof tierUsage?.dailyLimit === 'number' ? tierUsage.dailyLimit : 1000} />
            <TierBar label="Total Requests" used={tierUsage?.totalRequests} max={null} />
            <div style={{ fontSize: 13, color: 'var(--text-secondary)', marginTop: 8 }}>
              Rate Limit: <strong style={{ color: 'var(--text-primary)' }}>{tierUsage?.rateLimit || '10 req/sec'}</strong>
            </div>
            <div style={{ fontSize: 13, color: 'var(--text-secondary)' }}>
              Max Connections: <strong style={{ color: 'var(--text-primary)' }}>{tierUsage?.maxConnections || 5}</strong>
            </div>
          </div>
        </div>

        <div className="card">
          <div className="card-header">
            <h3>🕐 Recent Orders</h3>
            <span className="card-badge">{recentOrders?.length || 0}</span>
          </div>
          {recentOrders && recentOrders.length > 0 ? (
            <div style={{ maxHeight: 260, overflowY: 'auto' }}>
              {recentOrders.slice(0, 8).map(order => (
                <div key={order.orderId} style={{
                  display: 'flex', justifyContent: 'space-between', alignItems: 'center',
                  padding: '10px 0', borderBottom: '1px solid rgba(255,255,255,0.04)'
                }}>
                  <div>
                    <div style={{ fontWeight: 600, fontSize: 13 }}>{order.orderId}</div>
                    <div style={{ fontSize: 11, color: 'var(--text-muted)' }}>
                      {order.fulfilledBy || '—'} · {Object.keys(order.items || {}).length} items
                    </div>
                  </div>
                  <span className={`status-badge ${order.status?.toLowerCase()}`}>
                    {order.status}
                  </span>
                </div>
              ))}
            </div>
          ) : (
            <div className="empty-state">
              <div className="icon">📭</div>
              <p>No orders yet</p>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}

function TierBar({ label, used, max }) {
  const pct = max ? Math.min((used / max) * 100, 100) : 0
  const fillClass = pct > 80 ? 'danger' : pct > 50 ? 'warning' : ''

  return (
    <div className="tier-usage-item">
      <div className="usage-label">
        <span>{label}</span>
        <span>{used?.toLocaleString()}{max ? ` / ${max.toLocaleString()}` : ''}</span>
      </div>
      {max && (
        <div className="usage-bar">
          <div className={`usage-fill ${fillClass}`} style={{ width: `${pct}%` }} />
        </div>
      )}
    </div>
  )
}
