import { useState, useEffect, useCallback } from 'react'
import Sidebar from './components/Sidebar'
import Dashboard from './components/Dashboard'
import InventoryView from './components/InventoryView'
import OrdersView from './components/OrdersView'
import NewOrderView from './components/NewOrderView'

const API_KEY = 'demo-free-key'
const API_BASE = '/api/v1'

function App() {
  const [activeView, setActiveView] = useState('dashboard')
  const [dashboardData, setDashboardData] = useState(null)
  const [loading, setLoading] = useState(true)
  const [toasts, setToasts] = useState([])

  const addToast = useCallback((message, type = 'success') => {
    const id = Date.now()
    setToasts(prev => [...prev, { id, message, type }])
    setTimeout(() => setToasts(prev => prev.filter(t => t.id !== id)), 4000)
  }, [])

  const fetchDashboard = useCallback(async () => {
    try {
      const res = await fetch(`${API_BASE}/dashboard`, {
        headers: { 'X-API-Key': API_KEY }
      })
      if (!res.ok) throw new Error('Failed to fetch dashboard')
      const data = await res.json()
      setDashboardData(data)
      setLoading(false)
    } catch (err) {
      console.error('Dashboard fetch error:', err)
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    fetchDashboard()
    const interval = setInterval(fetchDashboard, 5000) // Refresh every 5s
    return () => clearInterval(interval)
  }, [fetchDashboard])

  const placeOrder = async (orderData) => {
    try {
      const res = await fetch(`${API_BASE}/orders`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'X-API-Key': API_KEY
        },
        body: JSON.stringify(orderData)
      })
      const data = await res.json()
      if (res.ok) {
        addToast(`Order ${data.orderId} placed at ${data.fulfilledBy}!`, 'success')
      } else {
        addToast(data.message || data.failureReason || 'Order failed', 'error')
      }
      fetchDashboard()
      return data
    } catch (err) {
      addToast('Network error placing order', 'error')
      return null
    }
  }

  const confirmOrder = async (orderId) => {
    try {
      const res = await fetch(`${API_BASE}/orders/${orderId}/confirm`, {
        method: 'POST',
        headers: { 'X-API-Key': API_KEY }
      })
      const data = await res.json()
      if (res.ok) addToast(`Order ${orderId} confirmed!`, 'success')
      else addToast(data.message || 'Confirm failed', 'error')
      fetchDashboard()
    } catch (err) {
      addToast('Network error', 'error')
    }
  }

  const cancelOrder = async (orderId) => {
    try {
      const res = await fetch(`${API_BASE}/orders/${orderId}/cancel`, {
        method: 'POST',
        headers: { 'X-API-Key': API_KEY }
      })
      const data = await res.json()
      if (res.ok) addToast(`Order ${orderId} cancelled, stock released`, 'success')
      else addToast(data.message || 'Cancel failed', 'error')
      fetchDashboard()
    } catch (err) {
      addToast('Network error', 'error')
    }
  }

  const renderView = () => {
    if (loading) return <div className="loading-spinner"><div className="spinner" /></div>

    switch (activeView) {
      case 'dashboard':
        return <Dashboard data={dashboardData} />
      case 'inventory':
        return <InventoryView data={dashboardData} />
      case 'orders':
        return <OrdersView data={dashboardData} onConfirm={confirmOrder} onCancel={cancelOrder} />
      case 'new-order':
        return <NewOrderView data={dashboardData} onPlaceOrder={placeOrder} />
      default:
        return <Dashboard data={dashboardData} />
    }
  }

  return (
    <div className="app-container">
      <Sidebar activeView={activeView} setActiveView={setActiveView}
               tier={dashboardData?.tierUsage?.tier || 'FREE'} />
      <main className="main-content">
        {renderView()}
      </main>
      <div className="toast-container">
        {toasts.map(t => (
          <div key={t.id} className={`toast ${t.type}`}>{t.message}</div>
        ))}
      </div>
    </div>
  )
}

export default App
