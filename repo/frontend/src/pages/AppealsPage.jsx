import { useEffect, useMemo, useRef, useState } from "react";
import { appealService, ticketService } from "../api";
import { Button } from "../components/Button";
import { Card } from "../components/Card";
import { EmptyState } from "../components/EmptyState";
import { Skeleton } from "../components/Skeleton";
import { StatusBadge } from "../components/StatusBadge";
import { useToast } from "../components/ToastProvider";
import { formatDateTime, pluralize } from "../utils/formatting";

const allowedTypes = ["image/jpeg", "image/png", "application/pdf"];

export default function AppealsPage() {
  const { showToast } = useToast();
  const fileInputRef = useRef(null);
  const [tickets, setTickets] = useState([]);
  const [appeals, setAppeals] = useState([]);
  const [selected, setSelected] = useState(null);
  const [form, setForm] = useState({ ticketId: "", reason: "", files: [] });
  const [errors, setErrors] = useState([]);
  const [loading, setLoading] = useState(true);
  const [dragOver, setDragOver] = useState(false);

  useEffect(() => {
    setLoading(true);
    Promise.all([
      ticketService.list({ page: 0, pageSize: 100 }),
      appealService.list({ page: 0, pageSize: 100 }),
    ])
      .then(([ticketRows, appealRows]) => {
        setTickets(ticketRows || []);
        setAppeals(appealRows || []);
      })
      .catch((error) =>
        showToast(error?.message || "Failed to load appeals", "error"),
      )
      .finally(() => setLoading(false));
  }, [showToast]);

  const fileSummary = useMemo(
    () => pluralize(form.files.length, "file"),
    [form.files.length],
  );

  const onFilesSelected = (files) => {
    const selectedFiles = Array.from(files || []);
    setForm((prev) => ({ ...prev, files: selectedFiles }));
    const nextErrors = validateFiles(selectedFiles);
    setErrors(nextErrors);
  };

  const onSubmit = async (event) => {
    event.preventDefault();
    const nextErrors = validateFiles(form.files);
    setErrors(nextErrors);
    if (nextErrors.length > 0) {
      return;
    }

    try {
      const response = await appealService.create({
        ticketId: form.ticketId,
        reason: form.reason,
        evidence: form.files,
      });
      setAppeals((prev) => [response.appeal || response, ...prev]);
      setForm({ ticketId: "", reason: "", files: [] });
      if (fileInputRef.current) {
        fileInputRef.current.value = "";
      }
      showToast("Appeal submitted", "success");
    } catch (error) {
      const details = error?.details?.files;
      if (Array.isArray(details)) {
        setErrors(details.map((row) => row.message || "Validation error"));
      }
      showToast(error?.message || "Failed to submit appeal", "error");
    }
  };

  const openAppeal = async (appealId) => {
    try {
      const detail = await appealService.detail(appealId);
      setSelected(detail);
    } catch (error) {
      showToast(error?.message || "Failed to open appeal", "error");
    }
  };

  const downloadEvidence = async (appealId, evidence) => {
    try {
      const response = await appealService.download(appealId, evidence.id);
      const blob = new Blob([response.data], { type: evidence.mime_type });
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement("a");
      anchor.href = url;
      anchor.download = evidence.file_name;
      anchor.click();
      URL.revokeObjectURL(url);
    } catch (error) {
      showToast(error?.message || "Download failed", "error");
    }
  };

  if (loading) {
    return (
      <div className="page-grid">
        <Card>
          <Skeleton className="skeleton-heading" />
          <Skeleton className="skeleton-block" />
        </Card>
        <Card>
          <Skeleton className="skeleton-heading" />
          <Skeleton className="skeleton-table" />
          <Skeleton className="skeleton-table" />
        </Card>
      </div>
    );
  }

  return (
    <div className="page-grid">
      <Card>
        <h2>Submit Appeal</h2>
        <form className="form-grid" onSubmit={onSubmit}>
          <label>
            Related Ticket
            <select
              required
              value={form.ticketId}
              onChange={(e) =>
                setForm((prev) => ({ ...prev, ticketId: e.target.value }))
              }
            >
              <option value="">Select a ticket</option>
              {tickets.map((ticket) => (
                <option key={ticket.id} value={ticket.id}>
                  {ticket.id.slice(0, 8)}... · {ticket.type}
                </option>
              ))}
            </select>
          </label>
          <label>
            Reason
            <textarea
              required
              minLength={10}
              maxLength={5000}
              value={form.reason}
              onChange={(e) =>
                setForm((prev) => ({ ...prev, reason: e.target.value }))
              }
            />
          </label>

          <div
            className={`drop-zone${dragOver ? " drop-zone-active" : ""}`}
            onDragOver={(e) => e.preventDefault()}
            onDragEnter={(e) => {
              e.preventDefault();
              setDragOver(true);
            }}
            onDragLeave={(e) => {
              e.preventDefault();
              setDragOver(false);
            }}
            onDrop={(e) => {
              e.preventDefault();
              setDragOver(false);
              onFilesSelected(e.dataTransfer.files);
            }}
          >
            <p>Drag and drop evidence files here</p>
            <small>Up to 5 files · Max 10MB each · JPG, PNG, PDF</small>
            <input
              ref={fileInputRef}
              type="file"
              multiple
              accept="image/jpeg,image/png,application/pdf"
              onChange={(e) => onFilesSelected(e.target.files)}
            />
            <p>{fileSummary}</p>
          </div>

          {errors.length > 0 && (
            <div className="error-box">
              {errors.map((error, index) => (
                <p key={index}>{error}</p>
              ))}
            </div>
          )}

          <Button type="submit">Submit Appeal</Button>
        </form>
      </Card>

      <Card>
        <h2>My Appeals</h2>
        {appeals.length === 0 ? (
          <EmptyState
            title="No appeals"
            message="Appeals you submit will appear here."
          />
        ) : (
          <div className="table-wrap">
            <table className="data-table">
              <thead>
                <tr>
                  <th>ID</th>
                  <th>Ticket</th>
                  <th>Status</th>
                  <th>Created</th>
                  <th></th>
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
                    <td>{formatDateTime(appeal.created_at)}</td>
                    <td>
                      <Button
                        variant="ghost"
                        onClick={() => openAppeal(appeal.id)}
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

      {selected?.appeal && (
        <Card>
          <h2>Appeal Detail</h2>
          <p>{selected.appeal.reason}</p>
          <p>
            <StatusBadge value={selected.appeal.status} /> · Reviewer notes:{" "}
            {selected.appeal.decision_notes || "Pending"}
          </p>
          <h3>Evidence</h3>
          {(selected.evidence || []).length === 0 ? (
            <p>No evidence uploaded.</p>
          ) : (
            <div className="evidence-grid">
              {selected.evidence.map((evidence) => (
                <article key={evidence.id} className="evidence-card">
                  <strong>{evidence.file_name}</strong>
                  <small>
                    {evidence.mime_type} ·{" "}
                    {(evidence.file_size_bytes / 1024 / 1024).toFixed(2)} MB
                  </small>
                  <Button
                    variant="secondary"
                    onClick={() =>
                      downloadEvidence(selected.appeal.id, evidence)
                    }
                  >
                    Download
                  </Button>
                </article>
              ))}
            </div>
          )}
        </Card>
      )}
    </div>
  );
}

function validateFiles(files) {
  const errors = [];
  if (files.length > 5) {
    errors.push("A maximum of 5 evidence files is allowed.");
  }

  files.forEach((file) => {
    if (file.size > 10 * 1024 * 1024) {
      errors.push(`${file.name}: file exceeds 10MB.`);
    }
    if (!allowedTypes.includes(file.type)) {
      errors.push(`${file.name}: unsupported file type.`);
    }
  });

  return errors;
}
