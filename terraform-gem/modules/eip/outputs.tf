output "db_allocation_id" {
  description = "Allocation ID for the DB EIP"
  value       = data.aws_eip.db.id
}

output "application_allocation_id" {
  description = "Allocation ID for the Application EIP"
  value       = data.aws_eip.application.id
}
