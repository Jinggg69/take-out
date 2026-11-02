"""Validate the synthetic phase-one fixture; never connects to a database."""
import json
import unittest
from decimal import Decimal
from pathlib import Path

FIXTURE = Path(__file__).resolve().parents[1] / 'database/fixtures/saas-isolation.json'


class IsolationFixtureTest(unittest.TestCase):
    def setUp(self):
        self.data = json.loads(FIXTURE.read_text(encoding='utf-8'))

    def test_two_tenants_three_stores_and_unique_ids(self):
        self.assertEqual([101, 102], [r['id'] for r in self.data['tenants']])
        self.assertEqual({101: 2, 102: 1}, {
            t['id']: sum(s['tenant_id'] == t['id'] for s in self.data['stores'])
            for t in self.data['tenants']})
        for table in ('tenants', 'stores', 'employees', 'users', 'orders', 'order_details'):
            ids = [r['id'] for r in self.data[table]]
            self.assertEqual(len(ids), len(set(ids)), table)

    def test_employee_scopes_stay_inside_tenant(self):
        stores = {s['id']: s['tenant_id'] for s in self.data['stores']}
        for employee in self.data['employees']:
            self.assertTrue(employee['allowed_store_ids'])
            for store in employee['allowed_store_ids']:
                self.assertEqual(employee['tenant_id'], stores[store])
        self.assertTrue(any(len(e['allowed_store_ids']) == 2 for e in self.data['employees']))
        self.assertTrue(any(e['allowed_store_ids'] == [1001] for e in self.data['employees']))

    def test_orders_have_valid_ownership_and_exact_totals(self):
        stores = {s['id']: s['tenant_id'] for s in self.data['stores']}
        users = {u['id'] for u in self.data['users']}
        orders = {o['id']: o for o in self.data['orders']}
        self.assertEqual(set(stores), {o['store_id'] for o in orders.values()})
        self.assertEqual(len(orders), len({o['number'] for o in orders.values()}))
        for detail in self.data['order_details']:
            self.assertIn(detail['order_id'], orders)
            self.assertGreater(detail['quantity'], 0)
        for order in orders.values():
            self.assertEqual(order['tenant_id'], stores[order['store_id']])
            self.assertIn(order['user_id'], users)
            details = [d for d in self.data['order_details'] if d['order_id'] == order['id']]
            self.assertTrue(details)
            total = sum(Decimal(d['unit_price']) * d['quantity'] for d in details)
            self.assertEqual(Decimal(order['amount']), total)

    def test_access_matrix_contains_positive_and_negative_controls(self):
        employees = {e['id']: e for e in self.data['employees']}
        users = {u['id'] for u in self.data['users']}
        orders = {o['id']: o for o in self.data['orders']}
        reasons = set()
        for case in self.data['access_expectations']:
            order = orders[case['order_id']]
            if case['actor_type'] == 'employee':
                actor = employees[case['actor_id']]
                allowed = (actor['tenant_id'] == order['tenant_id']
                           and order['store_id'] in actor['allowed_store_ids'])
            else:
                self.assertEqual('user', case['actor_type'])
                self.assertIn(case['actor_id'], users)
                allowed = case['actor_id'] == order['user_id']
            self.assertEqual(case['allowed'], allowed, case['reason'])
            reasons.add(case['reason'])
        self.assertTrue({'same_store', 'cross_store', 'cross_tenant', 'tenant_all_stores',
                         'own_order', 'other_user', 'shared_user_other_tenant'} <= reasons)

    def test_synthetic_identifiers_without_credentials(self):
        self.assertTrue(self.data['synthetic'])
        for user in self.data['users']:
            self.assertTrue(user['openid'].startswith('fixture-not-wechat-'))
            self.assertNotIn('tenant_id', user)  # User identity is not tenant membership.
        for employee in self.data['employees']:
            self.assertTrue(employee['username'].startswith('fixture_'))
            self.assertNotIn('password', employee)
        for order in self.data['orders']:
            self.assertTrue(order['number'].startswith('FIXTURE-'))


if __name__ == '__main__':
    unittest.main()
