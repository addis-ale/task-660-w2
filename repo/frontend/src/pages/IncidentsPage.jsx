import { useEffect, useState } from "react";
import { ticketService } from "../api/ticketService";
import { Button } from "../components/Button";
import { Card } from "../components/Card";
import { CountdownTimer } from "../components/CountdownTimer";
import { EmptyState } from "../components/EmptyState";
import { Skeleton } from "../components/Skeleton";
import { StatusBadge } from "../components/StatusBadge";
import { useToast } from "../components/ToastProvider";
import { formatDateTime } from "../utils/formatting";
import { maskPhone } from "../utils/masking";

function slaUrgencyClass(targetDate) {
  if (!targetDate) return "";
  const diff = new Date(targetDate).getTime() - Date.now();
  if (diff <= 0) return "sla-red";
  if (diff <= 3600000) return "sla-red";
  if (diff <= 14400000) return "sla-yellow";
  return "sla-green";
}

const ticketTemplate = {
  type: "DELIVERY_DISPUTE",
  severity: "LOW",
  description: "",
  location_address: "",
  location_cross_street: "",
};

export default function IncidentsPage() {
  const { showToast } = useToast();
  const [form, setForm] = useState(ticketTemplate);
  const [tickets, setTickets] = useState([]);
  const [selected, setSelected] = useState(null);
  const [followUpMessage, setFollowUpMessage] = useState("");
  const [loading, setLoading] = useState(true);

  const loadTickets = async () => {
    setLoading(true);
    try {
      const rows = await ticketService.list({ page: 0, pageSize: 50 });
      setTickets(rows || []);
    } catch (error) {
      showToast(error?.message || "Failed to load tickets", "error");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadTickets();
  }, []);

  const createTicket = async (event) => {
    event.preventDefault();
    try {
      const created = await ticketService.create(form);
      setForm(ticketTemplate);
      setTickets((prev) => [created, ...prev]);
      showToast("Ticket created", "success");
    } catch (error) {
      showToast(error?.message || "Failed to create ticket", "error");
    }
  };

  const openTicket = async (ticketId) => {
    try {
      const detail = await ticketService.detail(ticketId);
      setSelected(detail);
    } catch (error) {
      showToast(error?.message || "Failed to open ticket", "error");
    }
  };

  const addFollowUp = async (event) => {
    event.preventDefault();
    if (!selected?.ticket?.id || !followUpMessage.trim()) {
      return;
    }
    try {
      await ticketService.addFollowUp(selected.ticket.id, {
        message: followUpMessage,
      });
      const refreshed = await ticketService.detail(selected.ticket.id);
      setSelected(refreshed);
      setFollowUpMessage("");
      showToast("Follow-up added", "success");
    } catch (error) {
      showToast(error?.message || "Failed to add follow-up", "error");
    }
  };

  return (
    <div className="page-grid">
      <Card>
        <h2>Create Incident Ticket</h2>
        <form className="filters-grid" onSubmit={createTicket}>
          <label>
            Type
            <select
              value={form.type}
              onChange={(e) =>
                setForm((prev) => ({ ...prev, type: e.target.value }))
              }
            >
              <option value="DELIVERY_DISPUTE">Delivery Dispute</option>
              <option value="SAFETY_CONCERN">Safety Concern</option>
              <option value="PICKUP_ISSUE">Pickup Issue</option>
              <option value="OTHER">Other</option>
            </select>
          </label>
          <label>
            Severity
            <div className="inline-radio">
              {[
                ["LOW", "Low"],
                ["MEDIUM", "Medium"],
                ["HIGH", "High"],
              ].map(([value, label]) => (
                <label key={value}>
                  <input
                    type="radio"
                    name="severity"
                    checked={form.severity === value}
                    onChange={() =>
                      setForm((prev) => ({ ...prev, severity: value }))
                    }
                  />
                  {label}
                </label>
              ))}
            </div>
          </label>
          <label className="full-width">
            Description
            <textarea
              required
              minLength={10}
              maxLength={2000}
              value={form.description}
              onChange={(e) =>
                setForm((prev) => ({ ...prev, description: e.target.value }))
              }
            />
          </label>
          <label>
            Address
            <input
              value={form.location_address}
              onChange={(e) =>
                setForm((prev) => ({
                  ...prev,
                  location_address: e.target.value,
                }))
              }
            />
          </label>
          <label>
            Cross Street
            <input
              value={form.location_cross_street}
              onChange={(e) =>
                setForm((prev) => ({
                  ...prev,
                  location_cross_street: e.target.value,
                }))
              }
            />
          </label>
          <Button type="submit">Create Ticket</Button>
        </form>
      </Card>

      <Card>
        <h2>My Tickets</h2>
        {loading ? (
          <div className="stack-md">
            <Skeleton className="skeleton-table" />
            <Skeleton className="skeleton-table" />
            <Skeleton className="skeleton-table" />
          </div>
        ) : tickets.length === 0 ? (
          <EmptyState
            title="No tickets"
            message="Incident tickets will appear here."
          />
        ) : (
          <div className="table-wrap">
            <table className="data-table">
              <thead>
                <tr>
                  <th>ID</th>
                  <th>Type</th>
                  <th>Severity</th>
                  <th>Status</th>
                  <th>SLA</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {tickets.map((ticket) => (
                  <tr key={ticket.id}>
                    <td>{ticket.id.slice(0, 8)}...</td>
                    <td>{ticket.type}</td>
                    <td>{ticket.severity}</td>
                    <td>
                      <StatusBadge value={ticket.status} />
                    </td>
                    <td>
                      <span className={`sla-indicator ${slaUrgencyClass(ticket.sla_acknowledge_by)}`}>
                        <CountdownTimer
                          targetDate={ticket.sla_acknowledge_by}
                          label="Acknowledge by"
                        />
                      </span>
                    </td>
                    <td>
                      <Button
                        variant="ghost"
                        onClick={() => openTicket(ticket.id)}
                      >
                        Open
                      </Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      {selected?.ticket && (
        <Card>
          <h2>Ticket Detail</h2>
          <p>{selected.ticket.description}</p>
          {selected.ticket.reporter_phone && (
            <p>Reporter phone: {maskPhone(selected.ticket.reporter_phone)}</p>
          )}
          <p>
            Created: {formatDateTime(selected.ticket.created_at)} · Resolve by:{" "}
            {formatDateTime(selected.ticket.sla_resolve_by)}
          </p>
          <span className={`sla-indicator ${slaUrgencyClass(selected.ticket.sla_acknowledge_by)}`}>
            <CountdownTimer
              targetDate={selected.ticket.sla_acknowledge_by}
              label="Acknowledge by"
            />
          </span>
          <span className={`sla-indicator ${slaUrgencyClass(selected.ticket.sla_resolve_by)}`}>
            <CountdownTimer
              targetDate={selected.ticket.sla_resolve_by}
              label="Resolve by"
            />
          </span>

          <h3>Follow-up Thread</h3>
          <div className="follow-up-thread">
            {(selected.follow_ups || []).map((row) => (
              <article key={row.id} className="chat-bubble">
                <strong>{row.author_name}</strong>
                <p>{row.message}</p>
                <small>{formatDateTime(row.created_at)}</small>
              </article>
            ))}
          </div>
          <form className="inline-form" onSubmit={addFollowUp}>
            <input
              placeholder="Add follow-up"
              value={followUpMessage}
              onChange={(e) => setFollowUpMessage(e.target.value)}
            />
            <Button type="submit">Send</Button>
          </form>
        </Card>
      )}
    </div>
  );
}
