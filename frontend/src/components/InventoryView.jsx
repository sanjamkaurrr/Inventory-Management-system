export default function InventoryView({ data }) {
  if (!data) return <div className="loading-spinner"><div className="spinner" /></div>

  const { warehouses, inventorySummary } = data
  const warehouseIds = warehouses ? Object.keys(warehouses) : []
  const skus = inventorySummary ? Object.keys(inventorySummary) : []

  const getStockClass = (val) => {
    if (val === 0) return 'stock-zero'
    if (val < 20) return 'stock-low'
    if (val < 100) return 'stock-medium'
    return 'stock-high'
  }

  // Get per-warehouse per-sku data from full inventory
  const getSkuAtWarehouse = (sku, whId) => {
    if (!data.inventorySummary) return 0
    // We need per-warehouse data; use the dashboard warehouses stats
    // For a heatmap we'll compute from global totals proportionally
    // Actually let's fetch the detailed inventory
    return null
  }

  return (
    <div>
      <div className="page-header">
        <div>
          <h2>Inventory</h2>
          <p className="subtitle">Stock levels across all warehouses</p>
        </div>
        <div className="header-actions">
          <div className="live-indicator">
            <span className="live-dot" />
            Auto-refresh
          </div>
        </div>
      </div>

      {/* Global SKU Summary */}
      <div className="card" style={{ marginBottom: 20 }}>
        <div className="card-header">
          <h3>🔥 Stock Heatmap — Global Totals</h3>
          <span className="card-badge">{skus.length} SKUs</span>
        </div>
        <div style={{ overflowX: 'auto' }}>
          <div className="heatmap-grid" style={{ '--wh-count': 3 }}>
            {/* Header row */}
            <div className="heatmap-header">SKU</div>
            <div className="heatmap-header">Available</div>
            <div className="heatmap-header">Reserved</div>
            <div className="heatmap-header">Defective</div>

            {/* Data rows */}
            {skus.map(sku => {
              const s = inventorySummary[sku]
              return [
                <div key={`${sku}-label`} className="heatmap-sku">{sku}</div>,
                <div key={`${sku}-avail`} className={`heatmap-cell ${getStockClass(s?.available)}`}>
                  {(s?.available || 0).toLocaleString()}
                </div>,
                <div key={`${sku}-res`} className={`heatmap-cell ${s?.reserved > 20 ? 'stock-medium' : ''}`}>
                  {(s?.reserved || 0).toLocaleString()}
                </div>,
                <div key={`${sku}-def`} className={`heatmap-cell ${s?.defective > 5 ? 'stock-low' : ''}`}>
                  {(s?.defective || 0).toLocaleString()}
                </div>
              ]
            })}
          </div>
        </div>
      </div>

      {/* Per-Warehouse Detail Cards */}
      <div className="warehouse-grid" style={{ marginBottom: 20 }}>
        {warehouseIds.map(whId => {
          const wh = warehouses[whId]
          return (
            <div className="card" key={whId}>
              <div className="card-header">
                <h3>🏭 {wh.name}</h3>
              </div>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12, marginBottom: 12 }}>
                <StatBox label="Available" value={wh.totalAvailable} color="var(--accent-emerald)" />
                <StatBox label="Reserved" value={wh.totalReserved} color="var(--accent-amber)" />
                <StatBox label="Defective" value={wh.totalDefective} color="var(--accent-rose)" />
                <StatBox label="SKUs" value={wh.skuCount} color="var(--accent-indigo)" />
              </div>
              <div className="utilization-bar">
                <div className={`utilization-fill ${wh.utilizationPercent > 80 ? 'high' : wh.utilizationPercent > 50 ? 'medium' : ''}`}
                     style={{ width: `${Math.min(wh.utilizationPercent, 100)}%` }} />
              </div>
              <div style={{ fontSize: 12, color: 'var(--text-muted)', textAlign: 'right', marginTop: 4 }}>
                {wh.utilizationPercent}% of {(wh.maxCapacity || 0).toLocaleString()} capacity
              </div>
            </div>
          )
        })}
      </div>

      {/* Product catalog */}
      {data.products && (
        <div className="card">
          <div className="card-header">
            <h3>📋 Product Catalog</h3>
            <span className="card-badge">{Object.keys(data.products).length} products</span>
          </div>
          <table className="orders-table">
            <thead>
              <tr>
                <th>SKU</th>
                <th>Product Name</th>
                <th>Category</th>
                <th>Price</th>
                <th>Global Stock</th>
              </tr>
            </thead>
            <tbody>
              {Object.entries(data.products).map(([sku, p]) => (
                <tr key={sku}>
                  <td style={{ fontWeight: 600 }}>{sku}</td>
                  <td>{p.productName}</td>
                  <td>
                    <span className="status-badge pending">{p.category}</span>
                  </td>
                  <td>${(p.basePrice || 0).toFixed(2)}</td>
                  <td>
                    <span className={`status-badge ${(inventorySummary?.[sku]?.available || 0) > 50 ? 'confirmed' : 'failed'}`}>
                      {(inventorySummary?.[sku]?.available || 0).toLocaleString()}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}

function StatBox({ label, value, color }) {
  return (
    <div style={{
      background: 'var(--bg-glass)',
      border: '1px solid var(--border-glass)',
      borderRadius: 'var(--radius-sm)',
      padding: '12px',
      textAlign: 'center'
    }}>
      <div style={{ fontSize: 11, color: 'var(--text-muted)', textTransform: 'uppercase', letterSpacing: '0.5px' }}>
        {label}
      </div>
      <div style={{ fontSize: 22, fontWeight: 700, color, marginTop: 4 }}>
        {(value || 0).toLocaleString()}
      </div>
    </div>
  )
}
