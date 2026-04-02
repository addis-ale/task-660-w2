import { useEffect, useState } from "react";
import { appealService, riskService, ticketService } from "../api";
import { Button } from "../components/Button";
import { Card } from "../components/Card";
import { EmptyState } from "../components/EmptyState";
import { Modal } from "../components/Modal";
import { Skeleton } from "../components/Skeleton";
import { StatusBadge } from "../components/StatusBadge";
import { useToast } from "../components/ToastProvider";
import { formatDateTime } from "../utils/formatting";

function slaUrgencyClass(targetDate) {
  if (!targetDate) return "";
  const diff = new Date(targetDate).getTime() - Date.now();
  if (diff <= 0) return "sla-red";
  if (diff <= 3600000) return "sla-red";
  if (diff <= 14400000) return "sla-yellow";
  return "sla-green";
}

export default function ModerationHubPage() {
  const { showToast } = useToast();
  const [loading, setLoading] = useState(true);
  const [tickets, setTickets] = useState([]);
  const [selectedTicket, setSelectedTicket] = useState(null);
  const [appeals, setAppeals] = useState([]);
  const [selectedAppeal, setSelectedAppeal] = useState(null);
  const [risk, setRisk] = useState(null);

  const [resolveModal, setResolveModal] = useState({
    open: false,
    ticketId: null,
  });
  const [resolveForm, setResolveForm] = useState({
    closureCode: "RESOLVED_OK",
    closureNotes: "",
  });
  const [resolveErrors, setResolveErrors] = useState({});

  const [reviewModal, setReviewModal] = useState({
    open: false,
    appealId: null,
    decision: null,
  });
  const [reviewNotes, setReviewNotes] = useState("");
  const [reviewError, setReviewError] = useState("");

  useEffect(() => {
    setLoading(true);
    Promise.all([
      ticketService.list({ page: 0, pageSize: 100 }),
      appealService.list({ page: 0, pageSize: 100 }),
      riskService.dashboard(),
    ])
      .then(([ticketRows, appealRows, dashboard]) => {
        setTickets(ticketRows || []);
        setAppeals(appealRows || []);
        setRisk(dashboard);
      })
      .catch((error) =>
        showToast(error?.message || "Failed to load moderation hub", "error"),
      )
      .finally(() => setLoading(false));
  }, [showToast]);

  const acknowledge = async (ticketId) => {
    try {
      const updated = await ticketService.acknowledge(ticketId, {});
      setTickets((prev) =>
        prev.map((ticket) => (ticket.id === ticketId ? updated : ticket)),
      );
      showToast("Ticket acknowledged", "success");
    } catch (error) {
      showToast(error?.message || "Acknowledge failed", "error");
    }
  };

  const openResolveModal = (ticketId) => {
    setResolveModal({ open: true, ticketId });
    setResolveForm({ closureCode: "RESOLVED_OK", closureNotes: "" });
    setResolveErrors({});
  };

  const submitResolve = async () => {
    const errors = {};
    if (!resolveForm.closureCode.trim()) {
      errors.closureCode = "Closure code is required.";
    }
    if (resolveForm.closureNotes.trim().length < 3) {
      errors.closureNotes = "Closure notes must be at least 3 characters.";
    }
    setResolveErrors(errors);
    if (Object.keys(errors).length > 0) return;

    try {
      const updated = await ticketService.resolve(resolveModal.ticketId, {
        closure_code: resolveForm.closureCode,
        closure_notes: resolveForm.closureNotes,
      });
      setTickets((prev) =>
        prev.map((ticket) =>
          ticket.id === resolveModal.ticketId ? updated : ticket,
        ),
      );
      setResolveModal({ open: false, ticketId: null });
      showToast("Ticket resolved", "success");
    } catch (error) {
      showToast(error?.message || "Resolve failed", "error");
    }
  };

  const openReviewModal = (appealId, decision) => {
    setReviewModal({ open: true, appealId, decision });
    setReviewNotes("");
    setReviewError("");
  };

  const submitReview = async () => {
    if (reviewNotes.trim().length < 3) {
      setReviewError("Decision notes must be at least 3 characters.");
      return;
    }

    try {
      const updated = await appealService.review(reviewModal.appealId, {
        decision: reviewModal.decision,
        decision_notes: reviewNotes,
      });
      const flat = updated.appeal || updated;
      setAppeals((prev) =>
        prev.map((appeal) =>
          appeal.id === reviewModal.appealId ? flat : appeal,
        ),
      );
      setSelectedAppeal(updated);
      setReviewModal({ open: false, appealId: null, decision: null });
      showToast("Appeal reviewed", "success");
    } catch (error) {
      showToast(error?.message || "Appeal review failed", "error");
    }
  };

  if (loading) {
    return (
      <div className="page-grid">
        <Card>
          <Skeleton className="skeleton-heading" />
          <Skeleton className="skeleton-table" />
          <Skeleton className="skeleton-table" />
        </Card>
        <Card>
          <Skeleton className="skeleton-heading" />
          <Skeleton className="skeleton-table" />
        </Card>
        <Card>
          <Skeleton className="skeleton-heading" />
          <Skeleton className="skeleton-block" />
        </Card>
      </div>
    );
  }

  return (
    <div className="page-grid">
      <Card>
        <h2>Ticket Queue</h2>
        {tickets.length === 0 ? (
          <EmptyState
            title="No tickets"
            message="The ticket queue is empty."
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
                  <th>Assigned</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {tickets.map((ticket) => (
                  <tr
                    key={ticket.id}
                    className={
                      ticket.status === "ESCALATED" ? "row-highlight" : ""
                    }
                  >
                    <td>{ticket.id.slice(0, 8)}...</td>
                    <td>{ticket.type}</td>
                    <td>{ticket.severity}</td>
                    <td>
                      <StatusBadge value={ticket.status} />
                    </td>
                    <td>
                      <span
                        className={`sla-indicator ${slaUrgencyClass(ticket.sla_acknowledge_by)}`}
                      >
                        {formatDateTime(ticket.sla_acknowledge_by)}
                      </span>
                    </td>
                    <td>{ticket.assigned_to_name || "Unassigned"}</td>
                    <td className="actions-row">
                      <Button
                        variant="ghost"
                        onClick={() => setSelectedTicket(ticket)}
                      >
                        Open
                      </Button>
                      <Button
                        variant="secondary"
                        onClick={() => acknowledge(ticket.id)}
                      >
                        Acknowledge
                      </Button>
                      <Button onClick={() => openResolveModal(ticket.id)}>
                        Resolve
                      </Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      <Card>
        <h2>Appeal Review Panel</h2>
        {appeals.length === 0 ? (
          <EmptyState
            title="No appeals"
            message="The appeal queue is empty."
          />
        ) : (
          <div className="table-wrap">
            <table className="data-table">
              <thead>
                <tr>
                  <th>ID</th>
                  <th>Ticket</th>
                  <th>Status</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {appeals.map((appeal) => (
                  <tr key={appeal.id}>
                    <td>{appeal.id.slice(0, 8)}...</td>
                    <td>{appeal.ticket_id?.slice(0, 8)}...</td>
                    <td>
                      <StatusBadge value={appeal.status} />
                    </td>
                    <td className="actions-row">
                      <Button
                        variant="ghost"
                        onClick={async () =>
                          setSelectedAppeal(
                            await appealService.detail(appeal.id),
                          )
                        }
                      >
                        View
                      </Button>
                      <Button
                        variant="secondary"
                        onClick={() => openReviewModal(appeal.id, "APPROVED")}
                      >
                        Approve
                      </Button>
                      <Button
                        variant="danger"
                        onClick={() => openReviewModal(appeal.id, "DENIED")}
                      >
                        Deny
                      </Button>
                      <Button
                        onClick={() =>
                          openReviewModal(appeal.id, "ESCALATED_TO_ADMIN")
                        }
                      >
                        Escalate
                      </Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {selectedAppeal?.appeal && (
          <div className="detail-mini-panel">
            <h4>Appeal {selectedAppeal.appeal.id.slice(0, 8)}...</h4>
            <p>{selectedAppeal.appeal.reason}</p>
            <p>
              Evidence files: {selectedAppeal.evidence?.length || 0} · Last
              update {formatDateTime(selectedAppeal.appeal.created_at)}
            </p>
          </div>
        )}
      </Card>

      <Card>
        <h2>Risk Dashboard</h2>
        {risk ? (
          <div className="risk-grid">
            <article className="metric-card">
              <h4>Open Tickets</h4>
              <p>
                LOW {risk.open_tickets_low} · MEDIUM {risk.open_tickets_medium}{" "}
                · HIGH {risk.open_tickets_high}
              </p>
            </article>
            <article className="metric-card">
              <h4>Avg Resolution</h4>
              <p>{risk.avg_resolution_hours} hours</p>
            </article>
            <article className="metric-card">
              <h4>Escalation Rate</h4>
              <p>{risk.escalation_rate_percent}%</p>
            </article>
            <article className="metric-card">
              <h4>Active Flags</h4>
              <p>{risk.active_risk_flags}</p>
            </article>
            <article className="metric-card full-width">
              <h4>Top Flagged Sellers</h4>
              <ul className="plain-list">
                {(risk.top_flagged_sellers || []).map((row) => (
                  <li key={row.entity_id}>
                    {row.display_name}: {row.flag_type} ({row.incident_count})
                  </li>
                ))}
              </ul>
            </article>
            <article className="metric-card full-width">
              <h4>Top Flagged Members</h4>
              <ul className="plain-list">
                {(risk.top_flagged_members || []).map((row) => (
                  <li key={row.entity_id}>
                    {row.display_name}: {row.flag_type} ({row.incident_count})
                  </li>
                ))}
              </ul>
            </article>
          </div>
        ) : (
          <p>Loading risk dashboard...</p>
        )}
      </Card>

      {selectedTicket && (
        <Card>
          <h2>Escalation Inbox Detail</h2>
          <p>{selectedTicket.description}</p>
          <p>
            SLA Ack:{" "}
            <span
              className={`sla-indicator ${slaUrgencyClass(selectedTicket.sla_acknowledge_by)}`}
            >
              {formatDateTime(selectedTicket.sla_acknowledge_by)}
            </span>{" "}
            · SLA Resolve:{" "}
            <span
              className={`sla-indicator ${slaUrgencyClass(selectedTicket.sla_resolve_by)}`}
            >
              {formatDateTime(selectedTicket.sla_resolve_by)}
            </span>
          </p>
        </Card>
      )}

      <Modal
        title="Resolve Ticket"
        open={resolveModal.open}
        onClose={() => setResolveModal({ open: false, ticketId: null })}
      >
        <div className="form-grid">
          <label>
            Closure Code *
            <input
              value={resolveForm.closureCode}
              onChange={(e) =>
                setResolveForm((prev) => ({
                  ...prev,
                  closureCode: e.target.value,
                }))
              }
            />
            {resolveErrors.closureCode && (
              <span className="field-error">{resolveErrors.closureCode}</span>
            )}
          </label>
          <label>
            Closure Notes *
            <textarea
              rows={3}
              value={resolveForm.closureNotes}
              onChange={(e) =>
                setResolveForm((prev) => ({
                  ...prev,
                  closureNotes: e.target.value,
                }))
              }
            />
            {resolveErrors.closureNotes && (
              <span className="field-error">{resolveErrors.closureNotes}</span>
            )}
          </label>
          <div className="actions-row">
            <Button onClick={submitResolve}>Submit</Button>
            <Button
              variant="ghost"
              onClick={() => setResolveModal({ open: false, ticketId: null })}
            >
              Cancel
            </Button>
          </div>
        </div>
      </Modal>

      <Modal
        title="Appeal Review Notes"
        open={reviewModal.open}
        onClose={() =>
          setReviewModal({ open: false, appealId: null, decision: null })
        }
      >
        <div className="form-grid">
          <label>
            Decision Notes *
            <textarea
              rows={3}
              value={reviewNotes}
              onChange={(e) => setReviewNotes(e.target.value)}
            />
            {reviewError && <span className="field-error">{reviewError}</span>}
          </label>
          <div className="actions-row">
            <Button onClick={submitReview}>Submit</Button>
            <Button
              variant="ghost"
              onClick={() =>
                setReviewModal({ open: false, appealId: null, decision: null })
              }
            >
              Cancel
            </Button>
          </div>
        </div>
      </Modal>
    </div>
  );
}
