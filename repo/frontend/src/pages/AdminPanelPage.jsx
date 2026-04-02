import { useEffect, useState } from "react";
import { auditService, riskService, tierService, userService } from "../api";
import { Button } from "../components/Button";
import { Card } from "../components/Card";
import { EmptyState } from "../components/EmptyState";
import { StatusBadge } from "../components/StatusBadge";
import { useToast } from "../components/ToastProvider";
import { formatDateTime } from "../utils/formatting";
import { maskPhone } from "../utils/masking";

export default function AdminPanelPage() {
  const { showToast } = useToast();
  const [users, setUsers] = useState([]);
  const [tiers, setTiers] = useState([]);
  const [benefits, setBenefits] = useState([]);
  const [auditLogs, setAuditLogs] = useState([]);
  const [risk, setRisk] = useState(null);
  const [auditFilter, setAuditFilter] = useState({
    entityType: "",
    action: "",
    from: "",
    to: "",
  });

  useEffect(() => {
    Promise.allSettled([
      userService.list({ page: 0, pageSize: 50 }),
      tierService.listTiers(),
      tierService.listBenefits({}),
      auditService.list({ page: 0, pageSize: 50 }),
      riskService.dashboard(),
    ]).then((results) => {
      const [usersRes, tiersRes, benefitsRes, auditRes, riskRes] = results;
      setUsers(usersRes.status === "fulfilled" ? usersRes.value || [] : []);
      setTiers(tiersRes.status === "fulfilled" ? tiersRes.value || [] : []);
      setBenefits(
        benefitsRes.status === "fulfilled" ? benefitsRes.value || [] : [],
      );
      setAuditLogs(auditRes.status === "fulfilled" ? auditRes.value || [] : []);
      setRisk(riskRes.status === "fulfilled" ? riskRes.value : null);

      if (results.some((result) => result.status === "rejected")) {
        showToast(
          "Some admin datasets are unavailable yet in backend.",
          "warn",
        );
      }
    });
  }, [showToast]);

  const filteredAudit = auditLogs.filter((row) => {
    if (auditFilter.entityType && row.entity_type !== auditFilter.entityType)
      return false;
    if (auditFilter.action && row.action !== auditFilter.action) return false;
    if (
      auditFilter.from &&
      new Date(row.created_at) < new Date(auditFilter.from)
    )
      return false;
    if (auditFilter.to && new Date(row.created_at) > new Date(auditFilter.to))
      return false;
    return true;
  });

  return (
    <div className="page-grid">
      <Card>
        <h2>User Management</h2>
        {users.length === 0 ? (
          <EmptyState
            title="No user admin endpoint data"
            message="User management API can be wired when endpoint is exposed."
          />
        ) : (
          <div className="table-wrap">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Email</th>
                  <th>Phone</th>
                  <th>Role</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody>
                {users.map((user) => (
                  <tr key={user.id}>
                    <td>{user.email}</td>
                    <td>{maskPhone(user.phone)}</td>
                    <td>{user.role}</td>
                    <td>
                      <StatusBadge value={user.status} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      <Card>
        <h2>Tier & Benefit Configuration</h2>
        <div className="two-col">
          <article>
            <h3>Tiers</h3>
            <ul className="plain-list">
              {tiers.map((tier) => (
                <li key={tier.id}>
                  {tier.name} ({tier.rank}) — {tier.spend_threshold_min} to{" "}
                  {tier.spend_threshold_max || "∞"}
                </li>
              ))}
            </ul>
          </article>
          <article>
            <h3>Benefits</h3>
            <ul className="plain-list">
              {benefits.map((benefit) => (
                <li key={benefit.id}>
                  {benefit.name} · {benefit.type} · priority {benefit.priority}
                </li>
              ))}
            </ul>
          </article>
        </div>
      </Card>

      <Card>
        <h2>Audit Log Viewer</h2>
        <div className="filters-grid">
          <label>
            Entity Type
            <input
              value={auditFilter.entityType}
              onChange={(e) =>
                setAuditFilter((prev) => ({
                  ...prev,
                  entityType: e.target.value,
                }))
              }
            />
          </label>
          <label>
            Action
            <input
              value={auditFilter.action}
              onChange={(e) =>
                setAuditFilter((prev) => ({ ...prev, action: e.target.value }))
              }
            />
          </label>
          <label>
            From
            <input
              type="date"
              value={auditFilter.from}
              onChange={(e) =>
                setAuditFilter((prev) => ({ ...prev, from: e.target.value }))
              }
            />
          </label>
          <label>
            To
            <input
              type="date"
              value={auditFilter.to}
              onChange={(e) =>
                setAuditFilter((prev) => ({ ...prev, to: e.target.value }))
              }
            />
          </label>
        </div>
        {filteredAudit.length === 0 ? (
          <EmptyState
            title="No audit entries"
            message="No rows match your filter."
          />
        ) : (
          <div className="table-wrap">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Time</th>
                  <th>Entity</th>
                  <th>Action</th>
                  <th>Changes</th>
                </tr>
              </thead>
              <tbody>
                {filteredAudit.map((row) => (
                  <tr key={row.id}>
                    <td>{formatDateTime(row.created_at)}</td>
                    <td>
                      {row.entity_type} · {row.entity_id}
                    </td>
                    <td>{row.action}</td>
                    <td>
                      <pre className="json-viewer">
                        {JSON.stringify(row.changes, null, 2)}
                      </pre>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      <Card>
        <h2>Analytics Dashboard</h2>
        {risk ? (
          <div className="analytics-grid">
            <article className="metric-card">
              <h4>Ticket Trends</h4>
              <p>
                LOW: {risk.open_tickets_low} · MEDIUM:{" "}
                {risk.open_tickets_medium} · HIGH: {risk.open_tickets_high}
              </p>
            </article>
            <article className="metric-card">
              <h4>Risk Flags Over Time</h4>
              <p>Active risk flags: {risk.active_risk_flags}</p>
            </article>
            <article className="metric-card full-width">
              <h4>Escalation Performance</h4>
              <p>
                Escalation Rate: {risk.escalation_rate_percent}% · Avg
                resolution: {risk.avg_resolution_hours} hours
              </p>
            </article>
          </div>
        ) : (
          <p>Loading analytics...</p>
        )}
      </Card>
    </div>
  );
}
