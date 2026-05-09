export default function OrdersView({ data, onConfirm, onCancel }) {
  if (!data) return <div className="loading-spinner"><div className="spinner" /></div>

  const orders = data.recentOrders || []

  const formatTime = (ts) => {
    if (!ts) return '—'
    try { return new Date(ts).toLocaleString() } catch { return ts }
  }

  return (
    <div>
      <div className="page-header">
        <div>
          <h2>Orders</h2>
          <p className="subtitle">Order history and management</p>
        </div>
        <div className="header-actions">
          <div className="live-indicator">
            <span className="live-dot" />
            {orders.length} orders
          </div>
        </div>
      </div>

      {/* Status summary */}
      {data.orderStats?.statusCounts && (
        <div className="stats-grid" style={{ marginBottom: 20 }}>
          {Object.entries(data.orderStats.statusCounts).map(([status, count]) => (
            <div className={`stat-card ${status === 'CONFIRMED' ? 'emerald' : status === 'FAILED' ? 'rose' : status === 'RESERVED' ? 'amber' : 'indigo'}`} key={status}>
              <div className="stat-label">{status}</div>
              <div className="stat-value">{count}</div>
            </div>
          ))}
        </div>
      )}

      <div className="card">
        <div className="card-header">
          <h3>🧾 All Orders</h3>
        </div>
        {orders.length > 0 ? (
          <div style={{ overflowX: 'auto' }}>
            <table className="orders-table">
              <thead>
                <tr>
                  <th>Order ID</th>
                  <th>Status</th>
                  <th>Items</th>
                  <th>Warehouse</th>
                  <th>Created</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {orders.map(order => (
                  <tr key={order.orderId}>
                    <td style={{ fontWeight: 600, fontFamily: 'monospace' }}>{order.orderId}</td>
                    <td>
                      <span className={`status-badge ${order.status?.toLowerCase()}`}>
                        {order.status}
                      </span>
                    </td>
                    <td>
                      {order.items && Object.entries(order.items).map(([sku, qty]) => (
                        <div key={sku} style={{ fontSize: 12 }}>{sku}: ×{qty}</div>
                      ))}
                    </td>
                    <td>{order.fulfilledBy || '—'}</td>
                    <td style={{ fontSize: 12, color: 'var(--text-muted)' }}>{formatTime(order.createdAt)}</td>
                    <td>
                      {order.status === 'RESERVED' && (
                        <div style={{ display: 'flex', gap: 6 }}>
                          <button className="btn btn-success btn-sm" onClick={() => onConfirm(order.orderId)}>
                            ✓ Confirm
                          </button>
                          <button className="btn btn-danger btn-sm" onClick={() => onCancel(order.orderId)}>
                            ✕ Cancel
                          </button>
                        </div>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <div className="empty-state">
            <div className="icon">📭</div>
            <p>No orders yet. Go to "New Order" to place one!</p>
          </div>
        )}
      </div>
    </div>
  )
}
