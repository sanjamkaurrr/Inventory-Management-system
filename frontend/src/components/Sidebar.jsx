export default function Sidebar({ activeView, setActiveView, tier }) {
  const navItems = [
    { id: 'dashboard', icon: '📊', label: 'Dashboard' },
    { id: 'inventory', icon: '📦', label: 'Inventory' },
    { id: 'orders', icon: '🧾', label: 'Orders' },
    { id: 'new-order', icon: '➕', label: 'New Order' },
  ]

  return (
    <aside className="sidebar">
      <div className="sidebar-logo">
        <div className="logo-icon">📦</div>
        <div>
          <h1>InventoryHub</h1>
          <span>Warehouse Management</span>
        </div>
      </div>

      <nav className="sidebar-nav">
        {navItems.map(item => (
          <button key={item.id}
            className={`nav-item ${activeView === item.id ? 'active' : ''}`}
            onClick={() => setActiveView(item.id)}>
            <span className="icon">{item.icon}</span>
            {item.label}
          </button>
        ))}
      </nav>

      <div className="sidebar-footer">
        <div className={`tier-badge ${tier?.toLowerCase()}`}>
          ⚡ {tier} Tier
        </div>
      </div>
    </aside>
  )
}
