import { useState } from 'react'

export default function NewOrderView({ data, onPlaceOrder }) {
  const [warehouse, setWarehouse] = useState('')
  const [items, setItems] = useState([{ sku: '', quantity: 1 }])
  const [submitting, setSubmitting] = useState(false)
  const [result, setResult] = useState(null)

  const warehouseIds = data?.warehouses ? Object.keys(data.warehouses) : []
  const skus = data?.inventorySummary ? Object.keys(data.inventorySummary) : []

  const addItem = () => setItems([...items, { sku: '', quantity: 1 }])

  const removeItem = (index) => {
    if (items.length === 1) return
    setItems(items.filter((_, i) => i !== index))
  }

  const updateItem = (index, field, value) => {
    const updated = [...items]
    updated[index][field] = field === 'quantity' ? Math.max(1, parseInt(value) || 1) : value
    setItems(updated)
  }

  const handleSubmit = async (e) => {
    e.preventDefault()
    setSubmitting(true)
    setResult(null)

    const itemsMap = {}
    for (const item of items) {
      if (item.sku && item.quantity > 0) {
        itemsMap[item.sku] = (itemsMap[item.sku] || 0) + item.quantity
      }
    }

    if (Object.keys(itemsMap).length === 0) {
      setResult({ status: 'error', message: 'Add at least one valid item' })
      setSubmitting(false)
      return
    }

    const res = await onPlaceOrder({
      items: itemsMap,
      preferredWarehouse: warehouse || undefined
    })

    setResult(res)
    setSubmitting(false)

    if (res?.orderStatus === 'RESERVED') {
      setItems([{ sku: '', quantity: 1 }])
      setWarehouse('')
    }
  }

  return (
    <div>
      <div className="page-header">
        <div>
          <h2>Place New Order</h2>
          <p className="subtitle">Create an order with warehouse selection and SKU items</p>
        </div>
      </div>

      <div className="dashboard-grid">
        <div className="card">
          <div className="card-header">
            <h3>📝 Order Form</h3>
          </div>

          <form onSubmit={handleSubmit}>
            <div className="form-group">
              <label>Preferred Warehouse</label>
              <select className="form-select" value={warehouse} onChange={e => setWarehouse(e.target.value)}>
                <option value="">Auto-select (nearest available)</option>
                {warehouseIds.map(id => (
                  <option key={id} value={id}>{id} — {data.warehouses[id]?.name}</option>
                ))}
              </select>
            </div>

            <label style={{ fontSize: 13, fontWeight: 600, color: 'var(--text-secondary)', display: 'block', marginBottom: 8 }}>
              Order Items
            </label>

            {items.map((item, i) => (
              <div className="order-item-row" key={i}>
                <div className="form-group">
                  <select className="form-select" value={item.sku} onChange={e => updateItem(i, 'sku', e.target.value)}>
                    <option value="">Select SKU...</option>
                    {skus.map(sku => (
                      <option key={sku} value={sku}>
                        {sku} — {data.products?.[sku]?.productName || sku}
                        {' '}(avail: {data.inventorySummary?.[sku]?.available || 0})
                      </option>
                    ))}
                  </select>
                </div>
                <div className="form-group" style={{ maxWidth: 100 }}>
                  <input type="number" className="form-input" min="1" value={item.quantity}
                         onChange={e => updateItem(i, 'quantity', e.target.value)} placeholder="Qty" />
                </div>
                <button type="button" className="btn btn-ghost btn-sm" onClick={() => removeItem(i)}
                        style={{ marginBottom: 0 }}>✕</button>
              </div>
            ))}

            <button type="button" className="btn btn-ghost btn-sm" onClick={addItem} style={{ marginBottom: 20 }}>
              + Add Item
            </button>

            <div style={{ display: 'flex', gap: 12 }}>
              <button type="submit" className="btn btn-primary" disabled={submitting}>
                {submitting ? '⏳ Processing...' : '🚀 Place Order'}
              </button>
            </div>
          </form>

          {result && (
            <div style={{
              marginTop: 20, padding: 16, borderRadius: 'var(--radius-md)',
              background: result.orderStatus === 'RESERVED' || result.status === 'success'
                ? 'rgba(16, 185, 129, 0.1)' : 'rgba(244, 63, 94, 0.1)',
              border: `1px solid ${result.orderStatus === 'RESERVED' || result.status === 'success'
                ? 'rgba(16, 185, 129, 0.3)' : 'rgba(244, 63, 94, 0.3)'}`,
            }}>
              <div style={{ fontWeight: 700, marginBottom: 4 }}>
                {result.orderStatus === 'RESERVED' ? '✅ Order Placed!' : '❌ Order Failed'}
              </div>
              {result.orderId && <div style={{ fontSize: 13 }}>Order ID: <strong>{result.orderId}</strong></div>}
              {result.fulfilledBy && <div style={{ fontSize: 13 }}>Warehouse: <strong>{result.fulfilledBy}</strong></div>}
              {result.failureReason && <div style={{ fontSize: 13, color: 'var(--accent-rose)' }}>{result.failureReason}</div>}
              {result.message && <div style={{ fontSize: 13 }}>{result.message}</div>}
            </div>
          )}
        </div>

        {/* Quick stock reference */}
        <div className="card">
          <div className="card-header">
            <h3>📦 Available Stock</h3>
          </div>
          {data?.inventorySummary ? (
            <div style={{ maxHeight: 400, overflowY: 'auto' }}>
              {Object.entries(data.inventorySummary).map(([sku, stock]) => (
                <div key={sku} style={{
                  display: 'flex', justifyContent: 'space-between', alignItems: 'center',
                  padding: '10px 0', borderBottom: '1px solid rgba(255,255,255,0.04)'
                }}>
                  <div>
                    <div style={{ fontWeight: 600, fontSize: 13 }}>{sku}</div>
                    <div style={{ fontSize: 11, color: 'var(--text-muted)' }}>
                      {data.products?.[sku]?.productName || ''}
                    </div>
                  </div>
                  <div style={{ textAlign: 'right' }}>
                    <span className={`status-badge ${stock.available > 50 ? 'confirmed' : stock.available > 10 ? 'reserved' : 'failed'}`}>
                      {stock.available} avail
                    </span>
                  </div>
                </div>
              ))}
            </div>
          ) : (
            <div className="empty-state"><p>No inventory data</p></div>
          )}
        </div>
      </div>
    </div>
  )
}
