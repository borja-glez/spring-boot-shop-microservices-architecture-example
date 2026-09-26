import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    title: 'Mercado · Shop',
    loadComponent: () => import('./features/shop/catalog-page').then((m) => m.CatalogPage),
  },
  {
    path: 'products/:slug',
    title: 'Mercado · Product',
    loadComponent: () => import('./features/shop/product-page').then((m) => m.ProductPage),
  },
  {
    path: 'lab',
    title: 'Mercado · Filter lab',
    loadComponent: () => import('./features/lab/filter-lab-page').then((m) => m.FilterLabPage),
  },
  {
    path: 'cart',
    title: 'Mercado · Cart',
    loadComponent: () => import('./features/orders/cart-page').then((m) => m.CartPage),
  },
  {
    path: 'orders',
    title: 'Mercado · My orders',
    loadComponent: () => import('./features/orders/orders-page').then((m) => m.OrdersPage),
  },
  {
    path: 'orders/:id',
    title: 'Mercado · Order',
    loadComponent: () => import('./features/orders/order-page').then((m) => m.OrderPage),
  },
  {
    path: 'event-store',
    title: 'Mercado · Event store',
    loadComponent: () => import('./features/events/event-store-page').then((m) => m.EventStorePage),
  },
  {
    path: 'backoffice/stock',
    title: 'Mercado · Stock',
    loadComponent: () => import('./features/backoffice/stock-page').then((m) => m.StockPage),
  },
  {
    path: 'backoffice/payments',
    title: 'Mercado · Payments',
    loadComponent: () => import('./features/backoffice/payments-page').then((m) => m.PaymentsPage),
  },
  {
    path: 'backoffice/reports',
    title: 'Mercado · Reports',
    loadComponent: () => import('./features/backoffice/reports-page').then((m) => m.ReportsPage),
  },
  {
    path: 'notifications',
    title: 'Mercado · Notifications',
    loadComponent: () =>
      import('./features/notifications/notifications-page').then((m) => m.NotificationsPage),
  },
  {
    path: 'chaos',
    title: 'Mercado · Chaos',
    loadComponent: () => import('./features/chaos/chaos-page').then((m) => m.ChaosPage),
  },
  { path: '**', redirectTo: '' },
];
